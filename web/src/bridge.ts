import type { Card, Client, Command, Host, Shell } from '../../protocol/folio-v1';
import { parseHost } from './codec';

export type Transport = { document: string; postMessage: (json: string) => void };
export type Admission = { kind: 'ok' } | { kind: 'rejected'; code: 'stale' | 'invalid' | 'busy' | 'failed'; message: string };
type Listener = () => void;
export type View = { shell: Shell | null; turns: string[]; hydrated: boolean; problem: string };

declare global { interface Window { folioHost?: Transport } }

export class Bridge {
  private view: View = { shell: null, turns: [], hydrated: false, problem: '' };
  private cards = new Map<string, Card>();
  private listeners = new Set<Listener>();
  private cardListeners = new Map<string, Set<Listener>>();
  private pending = new Map<number, (result: Admission) => void>();
  private session: string | null = null;
  private hseq = 0;
  private cseq = 0;
  private recovering = false;
  private hydration: { shell: Shell; turns: string[]; cards: Map<string, Card> } | null = null;
  private target: EventTarget | null = null;
  constructor(readonly transport: Transport | undefined) {}

  getSnapshot = () => this.view;
  getCard = (turn: string) => this.cards.get(turn);
  subscribe = (listener: Listener) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  subscribeCard = (turn: string, listener: Listener) => {
    let listeners = this.cardListeners.get(turn);
    if (!listeners) { listeners = new Set(); this.cardListeners.set(turn, listeners); }
    listeners.add(listener);
    return () => { listeners.delete(listener); if (listeners.size === 0) this.cardListeners.delete(turn); };
  };
  private publish(next: View) { this.view = next; this.listeners.forEach(listener => listener()); }
  private publishCard(turn: string) { this.cardListeners.get(turn)?.forEach(listener => listener()); }
  private event = (event: Event) => { if (event instanceof CustomEvent) this.receive(event.detail); };
  start(target: EventTarget) {
    this.target = target;
    target.addEventListener('folio:host', this.event);
    this.ready();
    return () => {
      target.removeEventListener('folio:host', this.event);
      this.target = null;
      this.publish({ ...this.view, hydrated: false });
      this.rejectPending('IDE 连接已关闭');
    };
  }
  private rejectPending(message: string) {
    const pending = [...this.pending.values()];
    this.pending.clear();
    pending.forEach(resolve => resolve({ kind: 'rejected', code: 'stale', message }));
  }
  private ready() {
    if (!this.transport) return;
    const message: Client = { v: 1, document: this.transport.document, kind: 'ready' };
    this.transport.postMessage(JSON.stringify(message));
  }
  private recover(problem: string) {
    if (this.recovering) return;
    this.recovering = true;
    this.publish({ ...this.view, hydrated: false, problem });
    this.rejectPending('连接已重新同步，请重新操作');
    this.ready();
  }
  command = (command: Command): Promise<Admission> => {
    if (!this.target || !this.transport || !this.session || !this.view.hydrated)
      return Promise.resolve({ kind: 'rejected', code: 'busy', message: '正在连接 IDE' });
    const cseq = ++this.cseq;
    const message: Client = { v: 1, document: this.transport.document, session: this.session, cseq, kind: 'command', command };
    return new Promise(resolve => {
      this.pending.set(cseq, resolve);
      try { this.transport!.postMessage(JSON.stringify(message)); }
      catch { this.pending.delete(cseq); resolve({ kind: 'rejected', code: 'failed', message: '无法发送到 IDE' }); this.recover('IDE 连接中断'); }
    });
  };
  receive(value: unknown) {
    const message = parseHost(value);
    if (!this.target || !message || !this.transport || message.document !== this.transport.document) return;
    const { change } = message;
    if (change.kind === 'reset') {
      if (this.session === message.session && message.hseq <= this.hseq) return;
      this.rejectPending('连接已重新同步，请重新操作');
      this.session = message.session;
      this.hseq = message.hseq;
      this.cseq = change.processed;
      this.recovering = false;
      this.hydration = { shell: change.shell, turns: change.turns, cards: new Map() };
      this.publish({ ...this.view, hydrated: false, problem: '' });
      return;
    }
    if (!this.session || message.session !== this.session || this.recovering) return;
    if (message.hseq <= this.hseq) return;
    if (message.hseq !== this.hseq + 1) { this.recover('消息顺序已变化，正在重新同步'); return; }
    this.hseq = message.hseq;
    this.apply(change);
  }
  private apply(change: Exclude<Host['change'], { kind: 'reset' }>) {
    switch (change.kind) {
      case 'shell':
        if (this.hydration) this.hydration.shell = change.shell;
        else this.publish({ ...this.view, shell: change.shell });
        break;
      case 'hydrated': {
        const hydration = this.hydration;
        if (!hydration || hydration.turns.some(turn => !hydration.cards.has(turn))) { this.recover('对话尚未同步完整'); return; }
        const changed = new Set([...this.cards.keys(), ...hydration.cards.keys()]);
        this.cards = hydration.cards;
        this.hydration = null;
        this.publish({ shell: hydration.shell, turns: hydration.turns, hydrated: true, problem: '' });
        changed.forEach(turn => this.publishCard(turn));
        break;
      }
      case 'card': {
        const { card } = change;
        const cards = this.hydration?.cards ?? this.cards;
        if (this.hydration && !this.hydration.turns.includes(card.at.turn)) { this.recover('对话同步内容不匹配'); return; }
        const old = cards.get(card.at.turn);
        if (old && card.at.number < old.at.number) return;
        if (old && card.at.number === old.at.number && old.body.kind !== 'stream' && card.body.kind !== old.body.kind) return;
        if (!old && cards.size >= 40) { this.recover('对话数量超过同步范围'); return; }
        cards.set(card.at.turn, card);
        if (!this.hydration) {
          this.publishCard(card.at.turn);
          if (!this.view.turns.includes(card.at.turn)) this.publish({ ...this.view, turns: [...this.view.turns, card.at.turn] });
        }
        break;
      }
      case 'text': {
        const cards = this.hydration?.cards ?? this.cards;
        const card = cards.get(change.at.turn);
        if (!card || card.at.number !== change.at.number || card.body.kind !== 'stream') return;
        if (change.offset !== card.body.text.length) { this.recover('回答片段不连续，正在重新同步'); return; }
        cards.set(change.at.turn, { ...card, body: { ...card.body, text: card.body.text + change.text } });
        if (!this.hydration) this.publishCard(change.at.turn);
        break;
      }
      case 'evict': {
        if (this.hydration) {
          const hydration = this.hydration;
          change.turns.forEach(turn => hydration.cards.delete(turn));
          hydration.turns = hydration.turns.filter(turn => !change.turns.includes(turn));
          break;
        }
        change.turns.forEach(turn => { this.cards.delete(turn); this.publishCard(turn); });
        this.publish({ ...this.view, turns: this.view.turns.filter(turn => !change.turns.includes(turn)) }); break;
      }
      case 'ack': {
        const resolve = this.pending.get(change.cseq);
        if (resolve) { this.pending.delete(change.cseq); resolve(change.result); }
        if (change.result.kind === 'rejected') {
          if (change.result.code === 'stale') this.recover('连接或操作已变化，正在重新同步');
          else this.publish({ ...this.view, problem: change.result.message });
        }
        break;
      }
    }
  }
}

import { test as base, expect, type Page } from '@playwright/test';
import type { Card, Client, Command, Host, Shell } from '../../protocol/folio-v1';

export const draft = (text = '') => ({ text, dot: text.length, mark: text.length, revision: 0, epoch: 0, context: null });
export const initialShell = (): Shell => ({
  mode: 'ask', route: { kind: 'chat' },
  drafts: { ask: draft(), drill: draft(), tests: draft(), commit: draft() }, generation: null,
  provider: { revision: 0, phase: 'ready', endpoint: 'https://api.example.com/v1', model: 'test-model', key: 'bound', warning: '', settledForm: null },
  files: [], fileTask: null, fileWarnings: [],
  reading: { following: true, anchor: null, unseen: 0, expanded: [] },
  theme: { dark: true, reducedMotion: false, font: 'sans-serif', fontPx: 13, scale: 1 }, notice: '', confirm: null,
});
export const answer = (turn = 't-1', raw = '回答内容'): Card => ({
  at: { turn, number: 1 }, mode: 'ask', user: '示例问题', body: { kind: 'answer', raw, evidence: 'GENERAL', citations: [] },
  retry: { enabled: true, reason: '' }, writeTest: { enabled: false, reason: '没有可写入的测试' }, fillCommit: { enabled: false, reason: '没有原始提交框' }, generateTests: null,
});

class HostFixture {
  shell = initialShell();
  cards: Card[] = [];
  commands: Command[] = [];
  private sequence = 0;
  private ready = false;
  readonly page: Page;
  constructor(page: Page) { this.page = page; }
  async send(change: Host['change']) {
    const event: Host = { v: 1, document: 'acceptance-document', session: 'acceptance-session', hseq: ++this.sequence, change };
    await this.page.evaluate(message => window.dispatchEvent(new CustomEvent('folio:host', { detail: message })), event);
  }
  async publish() { await this.send({ kind: 'shell', shell: this.shell }); }
  async card(card: Card) {
    const index = this.cards.findIndex(value => value.at.turn === card.at.turn);
    if (index < 0) this.cards.push(card); else this.cards[index] = card;
    await this.send({ kind: 'card', card });
  }
  async open() {
    await this.page.exposeFunction('acceptancePost', async (json: string) => {
      const message: Client = JSON.parse(json);
      if (message.kind === 'ready') {
        await this.send({ kind: 'reset', processed: 0, shell: this.shell, turns: this.cards.map(card => card.at.turn) });
        for (const card of this.cards) await this.send({ kind: 'card', card });
        await this.send({ kind: 'hydrated' });
        this.ready = true;
        return;
      }
      this.commands.push(message.command);
      const command = message.command;
      switch (command.kind) {
        case 'mode': this.shell.mode = command.mode; break;
        case 'route': this.shell.route = command.to === 'settings' ? { kind: 'settings', edit: 'edit-1' } : { kind: command.to }; break;
        case 'cancelSettings': this.shell.route = { kind: 'chat' }; break;
        case 'previewTest': this.shell.route = { kind: 'testPreview', at: command.at, tab: command.tab }; break;
        case 'edit': {
          const current = this.shell.drafts[command.mode];
          if (current.epoch === command.epoch && current.revision === command.base)
            this.shell.drafts[command.mode] = { ...current, ...command.value, revision: current.revision + 1 };
          break;
        }
        case 'reading': this.shell.reading = command.value; break;
      }
      await this.send({ kind: 'ack', cseq: message.cseq, result: { kind: 'ok' } });
      await this.publish();
    });
    await this.page.addInitScript(() => {
      Object.defineProperty(window, 'folioHost', { value: {
        document: 'acceptance-document',
        postMessage: (json: string) => (window as Window & { acceptancePost: (json: string) => Promise<void> }).acceptancePost(json),
      } });
    });
    await this.page.goto('/');
    await expect.poll(() => this.ready).toBe(true);
    await expect(this.page.getByRole('textbox', { name: '消息', exact: true })).toBeVisible();
  }
}

export const test = base.extend<{ host: HostFixture }>({ host: async ({ page }, use) => { await use(new HostFixture(page)); } });
export { expect };

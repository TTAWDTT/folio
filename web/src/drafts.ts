import type { Draft, DraftValue, Mode, Shell } from '../../protocol/folio-v1';
import type { Admission, Bridge } from './bridge';

export const modes: Mode[] = ['ask', 'drill', 'tests', 'commit'];
export const modeNames: Record<Mode, string> = { ask: '课程问答', drill: '课程练习', tests: '生成测试', commit: '提交信息' };
export const modeShortNames: Record<Mode, string> = { ask: '问答', drill: '练习', tests: '生成测试', commit: '提交信息' };
type Pending = { value: DraftValue; base: number; epoch: number };
export type Editor = { canonical: Draft; value: DraftValue; pending: Pending | null; dirty: boolean; conflict: boolean };
type Waiter = { mode: Mode; resolve: () => void };
const equal = (a: DraftValue, b: DraftValue) => a.text === b.text && a.dot === b.dot && a.mark === b.mark;

export class Drafts {
  private editors = new Map<Mode, Editor>();
  private listeners = new Set<() => void>();
  private waiters: Waiter[] = [];
  private lastShell: Shell | null = null;
  private version = 0;
  private sends = new Map<Mode, { phase: 'waiting' | 'admitted'; base: number; epoch: number }>();
  constructor(private bridge: Bridge) { bridge.subscribe(() => this.sync()); this.sync(); }
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  getSnapshot = () => this.version;
  get = (mode: Mode) => this.editors.get(mode);
  private changed() { this.version++; this.listeners.forEach(listener => listener()); }
  private sync() {
    const view = this.bridge.getSnapshot();
    const shell = view.shell;
    if (!shell) return;
    if (shell === this.lastShell) { if (view.hydrated) for (const mode of modes) this.pump(mode); return; }
    this.lastShell = shell;
    for (const mode of modes) {
      const incoming = shell.drafts[mode];
      const sending = this.sends.get(mode);
      if (sending?.phase === 'admitted' && (incoming.revision > sending.base || incoming.epoch !== sending.epoch)) this.sends.delete(mode);
      const editor = this.editors.get(mode);
      if (!editor) { this.editors.set(mode, { canonical: incoming, value: incoming, pending: null, dirty: false, conflict: false }); continue; }
      if (incoming.epoch !== editor.canonical.epoch) {
        const hasLocal = editor.dirty || editor.pending !== null;
        this.editors.set(mode, { canonical: incoming, value: hasLocal ? editor.value : incoming, pending: null, dirty: hasLocal, conflict: hasLocal });
        continue;
      }
      if (incoming.revision <= editor.canonical.revision) continue;
      if (editor.pending && incoming.revision > editor.pending.base && incoming.text === editor.pending.value.text) {
        const dirty = !equal(editor.value, editor.pending.value);
        this.editors.set(mode, { canonical: incoming, value: dirty ? editor.value : incoming, pending: null, dirty, conflict: false });
      } else if (editor.pending || editor.dirty) {
        this.editors.set(mode, { ...editor, canonical: incoming });
      } else this.editors.set(mode, { canonical: incoming, value: incoming, pending: null, dirty: false, conflict: false });
    }
    this.changed();
    for (const mode of modes) this.pump(mode);
    this.resolveWaiters();
  }
  edit(mode: Mode, value: DraftValue) {
    const editor = this.editors.get(mode);
    if (!editor || equal(editor.value, value)) return;
    this.editors.set(mode, { ...editor, value, dirty: true });
    this.changed();
    this.pump(mode);
  }
  private pump(mode: Mode) {
    const editor = this.editors.get(mode);
    if (!editor || editor.conflict || editor.pending || this.sends.has(mode) || !editor.dirty || !this.bridge.getSnapshot().hydrated) return;
    const pending = { value: editor.value, base: editor.canonical.revision, epoch: editor.canonical.epoch };
    this.editors.set(mode, { ...editor, pending, dirty: false });
    void this.bridge.command({ kind: 'edit', mode, base: pending.base, epoch: pending.epoch, value: pending.value }).then(result => {
      if (result.kind === 'ok') return;
      const current = this.editors.get(mode)!;
      if (current.pending !== pending) return;
      this.editors.set(mode, { ...current, pending: null, dirty: true, conflict: true });
      this.changed();
      this.resolveWaiters();
    });
  }
  resolveConflict(mode: Mode, reapply: boolean) {
    const editor = this.editors.get(mode)!;
    this.editors.set(mode, { ...editor, conflict: false, value: reapply ? editor.value : editor.canonical, dirty: reapply, pending: null });
    this.changed();
    if (reapply) this.pump(mode);
    this.resolveWaiters();
  }
  private resolveWaiters() {
    const settled = this.waiters.filter(waiter => {
      const editor = this.editors.get(waiter.mode)!;
      return editor.conflict || !editor.pending;
    });
    this.waiters = this.waiters.filter(waiter => !settled.includes(waiter));
    settled.forEach(waiter => waiter.resolve());
  }
  async send(mode: Mode, epoch: number, value: DraftValue): Promise<Admission> {
    if (this.sends.has(mode)) return { kind: 'rejected', code: 'busy', message: '正在确认上一条发送' };
    this.pump(mode);
    const before = this.editors.get(mode)!;
    this.sends.set(mode, { phase: 'waiting', base: before.canonical.revision, epoch });
    if (before.pending) await new Promise<void>(resolve => this.waiters.push({ mode, resolve }));
    const editor = this.editors.get(mode)!;
    if (editor.conflict || editor.canonical.epoch !== epoch) {
      this.sends.delete(mode);
      return { kind: 'rejected', code: 'stale', message: 'IDE 已更换上下文，请先处理草稿冲突' };
    }
    this.sends.set(mode, { phase: 'admitted', base: editor.canonical.revision, epoch });
    const result = await this.bridge.command({ kind: 'send', mode, epoch, base: editor.canonical.revision, value });
    if (result.kind === 'rejected') { this.sends.delete(mode); this.pump(mode); }
    return result;
  }
}

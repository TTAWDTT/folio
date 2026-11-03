import { describe, expect, it } from 'vitest';
import { Drafts } from './drafts';
import { initialShell, testHost } from './test-host';

const value = (text: string) => ({ text, dot: text.length, mark: text.length });
const tick = async () => { await Promise.resolve(); await Promise.resolve(); };

describe('pending editor ownership', () => {
  it('coalesces typing behind acknowledgement without overwriting newer text', () => {
    const host = testHost(); const shell = initialShell(); host.hydrate(shell); const drafts = new Drafts(host.bridge);
    drafts.edit('ask', value('first')); drafts.edit('ask', value('newer'));
    expect(host.commands()).toHaveLength(1);
    host.ack(); shell.drafts.ask = { ...shell.drafts.ask, ...value('first'), revision: shell.drafts.ask.revision + 1 }; host.send({ kind: 'shell', shell });
    expect(drafts.get('ask')?.value.text).toBe('newer');
    expect(host.commands()[1].command).toMatchObject({ kind: 'edit', base: shell.drafts.ask.revision, value: value('newer') });
  });
  it('preserves text and selection separately in all modes', () => {
    const host = testHost(); host.hydrate(); const drafts = new Drafts(host.bridge);
    for (const mode of ['ask', 'drill', 'tests', 'commit'] as const) drafts.edit(mode, { text: mode, dot: 1, mark: 0 });
    for (const mode of ['ask', 'drill', 'tests', 'commit'] as const) expect(drafts.get(mode)?.value).toEqual({ text: mode, dot: 1, mark: 0 });
  });
  it('keeps a conflicting local buffer visible after a native epoch replacement until explicit reapply', () => {
    const host = testHost(); const shell = initialShell(); host.hydrate(shell); const drafts = new Drafts(host.bridge);
    drafts.edit('ask', value('local text'));
    shell.drafts.ask = { ...shell.drafts.ask, ...value('native text'), context: 'New.java', revision: 10, epoch: 1 }; host.send({ kind: 'shell', shell });
    expect(drafts.get('ask')).toMatchObject({ conflict: true, value: value('local text'), canonical: { context: 'New.java' } });
    expect(host.commands()).toHaveLength(1);
    drafts.resolveConflict('ask', true);
    expect(host.commands()[1].command).toMatchObject({ kind: 'edit', base: 10, epoch: 1, value: value('local text') });
  });
  it('can discard a conflict without silently sending it onto new context', () => {
    const host = testHost(); const shell = initialShell(); host.hydrate(shell); const drafts = new Drafts(host.bridge);
    drafts.edit('tests', value('unsent scenario'));
    shell.drafts.tests = { ...shell.drafts.tests, ...value('IDE scenario'), revision: 10, epoch: 2 }; host.send({ kind: 'shell', shell });
    drafts.resolveConflict('tests', false);
    expect(drafts.get('tests')).toMatchObject({ value: value('IDE scenario'), conflict: false, dirty: false });
    expect(host.commands()).toHaveLength(1);
  });
  it('flushes earlier edits before the captured Send while preserving post-press next draft', async () => {
    const host = testHost(); const shell = initialShell(); host.hydrate(shell); const drafts = new Drafts(host.bridge);
    drafts.edit('ask', value('captured'));
    const sending = drafts.send('ask', shell.drafts.ask.epoch, value('captured'));
    drafts.edit('ask', value('next draft'));
    host.ack(); shell.drafts.ask = { ...shell.drafts.ask, ...value('captured'), revision: shell.drafts.ask.revision + 1 }; host.send({ kind: 'shell', shell });
    await tick();
    expect(host.commands().map(message => message.command.kind)).toEqual(['edit', 'send']);
    expect(host.commands()[1].command).toMatchObject({ value: value('captured'), base: shell.drafts.ask.revision });
    host.ack(); shell.drafts.ask = { ...shell.drafts.ask, ...value(''), revision: shell.drafts.ask.revision + 1 }; host.send({ kind: 'shell', shell });
    await sending;
    expect(drafts.get('ask')?.value.text).toBe('next draft');
    expect(host.commands()[2].command).toMatchObject({ kind: 'edit', value: value('next draft'), base: shell.drafts.ask.revision });
  });
  it('retains pending input across reset and never replays it automatically', async () => {
    const host = testHost(); const shell = initialShell(); host.hydrate(shell); const drafts = new Drafts(host.bridge);
    drafts.edit('ask', value('pending buffer'));
    host.send({ kind: 'reset', processed: 0, shell, turns: [] }); host.send({ kind: 'hydrated' }); await tick();
    expect(drafts.get('ask')).toMatchObject({ conflict: true, value: value('pending buffer') });
    expect(host.commands()).toHaveLength(1);
  });
});

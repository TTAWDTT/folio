import { describe, expect, it } from 'vitest';
import { Bridge } from './bridge';
import { initialShell, testCard, testHost } from './test-host';

describe('IDE bridge lifecycle and ordered stream', () => {
  it('installs the listener before sending ready, including a synchronous host', () => {
    const target = new EventTarget();
    const bridge = new Bridge({ document: 'doc', postMessage: () => {
      target.dispatchEvent(new CustomEvent('folio:host', { detail: { v: 1, document: 'doc', session: 's', hseq: 1, change: { kind: 'reset', processed: 0, shell: initialShell(), turns: [] } } }));
      target.dispatchEvent(new CustomEvent('folio:host', { detail: { v: 1, document: 'doc', session: 's', hseq: 2, change: { kind: 'hydrated' } } }));
    } });
    bridge.start(target);
    expect(bridge.getSnapshot().shell).not.toBeNull();
    expect(bridge.getSnapshot().hydrated).toBe(true);
  });
  it('disables commands until every retained card has hydrated', async () => {
    const host = testHost();
    host.send({ kind: 'reset', processed: 0, shell: initialShell(), turns: ['missing'] });
    expect((await host.bridge.command({ kind: 'newChat' })).kind).toBe('rejected');
    host.send({ kind: 'hydrated' });
    expect(host.bridge.getSnapshot().hydrated).toBe(false);
    expect(host.posted.filter(message => message.kind === 'ready')).toHaveLength(2);
  });
  it('publishes retained shell and cards atomically at the hydration barrier', () => {
    const host = testHost(); const card = testCard();
    host.send({ kind: 'reset', processed: 0, shell: initialShell(), turns: ['t1'] });
    host.send({ kind: 'card', card });
    expect(host.bridge.getSnapshot().shell).toBeNull();
    expect(host.bridge.getCard('t1')).toBeUndefined();
    host.send({ kind: 'hydrated' });
    expect(host.bridge.getSnapshot().hydrated).toBe(true);
    expect(host.bridge.getCard('t1')).toEqual(card);
  });
  it('recovers a stale admission from its typed code without replaying commands', async () => {
    const host = testHost(); host.hydrate();
    const pending = host.bridge.command({ kind: 'deliverCommit', at: { turn: 't', number: 1 }, index: 0, delivery: 'copy' });
    host.send({ kind: 'ack', cseq: 1, result: { kind: 'rejected', code: 'stale', message: 'arbitrary localized text' } });
    await expect(pending).resolves.toMatchObject({ kind: 'rejected', code: 'stale' });
    expect(host.bridge.getSnapshot().hydrated).toBe(false);
    host.send({ kind: 'reset', processed: 0, shell: initialShell(), turns: [] }); host.send({ kind: 'hydrated' });
    void host.bridge.command({ kind: 'newChat' });
    expect(host.commands().map(message => message.command.kind)).toEqual(['deliverCommit', 'newChat']);
    expect(host.commands().map(message => message.cseq)).toEqual([1, 1]);
  });
  it('acknowledges admission without serializing Stop behind other settlement', async () => {
    const host = testHost(); host.hydrate();
    const choose = host.bridge.command({ kind: 'chooseFiles' });
    const stop = host.bridge.command({ kind: 'stop', at: { turn: 't', number: 1 } });
    expect(host.commands().map(message => message.cseq)).toEqual([1, 2]);
    host.ack(1); host.ack(0);
    await expect(stop).resolves.toEqual({ kind: 'ok' });
    await expect(choose).resolves.toEqual({ kind: 'ok' });
  });
  it('requests a fresh hydration on a sequence gap and never replays effects', async () => {
    const host = testHost(); host.hydrate();
    const pending = host.bridge.command({ kind: 'writeTest', at: { turn: 't', number: 1 } });
    host.send({ kind: 'shell', shell: initialShell() }, 7);
    await expect(pending).resolves.toMatchObject({ kind: 'rejected' });
    expect(host.bridge.getSnapshot().hydrated).toBe(false);
    expect(host.commands()).toHaveLength(1);
    expect(host.posted.filter(message => message.kind === 'ready')).toHaveLength(2);
    host.send({ kind: 'reset', processed: 1, shell: initialShell(), turns: [] }); host.send({ kind: 'hydrated' });
    void host.bridge.command({ kind: 'newChat' });
    expect(host.commands().map(message => message.cseq)).toEqual([1, 2]);
  });
  it('ignores another document, stale sequences and unknown message fields', () => {
    const host = testHost(); host.hydrate();
    const before = host.bridge.getSnapshot();
    host.bridge.receive({ v: 1, document: 'other', session: 'session', hseq: 3, change: { kind: 'shell', shell: initialShell() } });
    host.bridge.receive({ v: 1, document: 'doc', session: 'session', hseq: 3, surprise: true, change: { kind: 'shell', shell: initialShell() } });
    host.send({ kind: 'shell', shell: initialShell() }, 1);
    expect(host.bridge.getSnapshot()).toBe(before);
  });
  it('changes only the streaming card and leaves unrelated cards and shell subscribers stable', () => {
    const host = testHost(); const first = testCard(); const second = { ...testCard(), at: { turn: 't2', number: 1 } }; host.hydrate(initialShell(), [first, second]);
    const other = host.bridge.getCard('t2'); let shellCalls = 0; let cardCalls = 0;
    host.bridge.subscribe(() => shellCalls++); host.bridge.subscribeCard('t1', () => cardCalls++);
    host.send({ kind: 'text', at: first.at, offset: 0, text: 'hello' });
    expect(host.bridge.getCard('t1')?.body).toEqual({ kind: 'stream', phase: 'working', text: 'hello' });
    expect(host.bridge.getCard('t2')).toBe(other); expect(shellCalls).toBe(0); expect(cardCalls).toBe(1);
  });
  it('treats terminal replacement as authoritative and rejects late old-attempt updates', () => {
    const host = testHost(); const card = testCard(); host.hydrate(initialShell(), [card]);
    host.send({ kind: 'card', card: { ...card, at: { turn: 't1', number: 2 }, body: { kind: 'stopped', text: 'accepted' } } });
    host.send({ kind: 'text', at: card.at, offset: 0, text: 'late' });
    host.send({ kind: 'card', card });
    host.send({ kind: 'card', card: { ...card, at: { turn: 't1', number: 2 } } });
    expect(host.bridge.getCard('t1')?.body).toEqual({ kind: 'stopped', text: 'accepted' });
  });
  it('recovers an offset mismatch instead of duplicating text', () => {
    const host = testHost(); const card = testCard(); host.hydrate(initialShell(), [card]);
    host.send({ kind: 'text', at: card.at, offset: 10, text: 'bad offset' });
    expect(host.bridge.getSnapshot().hydrated).toBe(false);
    expect(host.bridge.getCard('t1')?.body).toEqual(card.body);
  });
  it('evicts retained card data and ignores callbacks after disposal', () => {
    const host = testHost(); const card = testCard(); host.hydrate(initialShell(), [card]);
    host.send({ kind: 'evict', turns: ['t1'] }); expect(host.bridge.getCard('t1')).toBeUndefined();
    host.close(); host.send({ kind: 'card', card }); expect(host.bridge.getCard('t1')).toBeUndefined();
  });
});

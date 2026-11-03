import type { Card, Client, Host, Shell } from '../../protocol/folio-v1';
import fixtures from '../../protocol/conformance-v1.json';
import { Bridge } from './bridge';
import { parseHost } from './codec';

export function initialShell(): Shell {
  const parsed = parseHost(fixtures.validHost.find(fixture => fixture.value.change.kind === 'reset')!.value);
  if (parsed?.change.kind !== 'reset') throw new Error('Missing valid reset fixture');
  return parsed.change.shell;
}
export function testCard(): Card {
  return { at: { turn: 't1', number: 1 }, mode: 'ask', user: 'question', body: { kind: 'stream', phase: 'working', text: '' }, retry: { enabled: false, reason: 'working' }, writeTest: { enabled: false, reason: '' }, fillCommit: { enabled: false, reason: '' }, generateTests: null };
}
export function testHost() {
  const posted: Client[] = [];
  const bridge = new Bridge({ document: 'doc', postMessage: json => { posted.push(JSON.parse(json)); } });
  const target = new EventTarget();
  let sequence = 0;
  const send = (change: Host['change'], hseq = ++sequence) => {
    sequence = hseq;
    target.dispatchEvent(new CustomEvent('folio:host', { detail: { v: 1, document: 'doc', session: 'session', hseq, change } }));
  };
  const close = bridge.start(target);
  const hydrate = (shell = initialShell(), cards: Card[] = []) => {
    send({ kind: 'reset', processed: 0, shell, turns: cards.map(card => card.at.turn) });
    cards.forEach(card => send({ kind: 'card', card }));
    send({ kind: 'hydrated' });
  };
  const commands = () => posted.filter(message => message.kind === 'command');
  const ack = (index = commands().length - 1) => {
    const message = commands()[index];
    send({ kind: 'ack', cseq: message.cseq, result: { kind: 'ok' } });
  };
  return { bridge, posted, send, close, hydrate, commands, ack };
}

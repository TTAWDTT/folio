import { describe, expect, it } from 'vitest';
import fixtures from '../../protocol/conformance-v1.json';
import { parseHost } from './codec';

describe('frozen v1 host conformance', () => {
  for (const fixture of fixtures.validHost) it(`accepts ${fixture.name}`, () => { expect(parseHost(fixture.value)).not.toBeNull(); });
  for (const fixture of fixtures.invalidHost) it(`rejects ${fixture.name}`, () => { expect(parseHost(fixture.value)).toBeNull(); });
  it('accepts eligible and disabled card actions and requires the nullable field', () => {
    const fixture = fixtures.validHost.find(fixture => fixture.name === 'card-answer')!;
    const wire = JSON.parse(JSON.stringify(fixture.value));
    wire.change.card.generateTests = { enabled: true, reason: '' };
    expect(parseHost(wire)).not.toBeNull();
    wire.change.card.generateTests = { enabled: false, reason: 'Unavailable source' };
    expect(parseHost(wire)).not.toBeNull();
    delete wire.change.card.generateTests;
    expect(parseHost(wire)).toBeNull();
  });
  it('rejects credentials added anywhere in the provider projection', () => {
    const reset = fixtures.validHost.find(fixture => fixture.value.change.kind === 'reset')!;
    const wire = JSON.parse(JSON.stringify(reset.value));
    wire.change.shell.provider.secret = 'must-not-be-admitted';
    expect(parseHost(wire)).toBeNull();
  });
  it('rejects a caret outside the actual draft text', () => {
    const reset = fixtures.validHost.find(fixture => fixture.value.change.kind === 'reset')!;
    const wire = JSON.parse(JSON.stringify(reset.value));
    wire.change.shell.drafts.ask.dot = 100_000;
    expect(parseHost(wire)).toBeNull();
  });
});

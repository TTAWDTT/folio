import { z } from 'zod';
import type { Host } from '../../protocol/folio-v1';

const text = z.string().max(2_000_000);
const id = z.string().min(1).max(512);
const number = z.number().int().nonnegative().max(Number.MAX_SAFE_INTEGER);
const positive = number.min(1);
const mode = z.enum(['ask', 'drill', 'tests', 'commit']);
const at = z.strictObject({ turn: id, number: positive });
const draft = z.strictObject({ text, dot: number, mark: number, revision: number, epoch: number, context: text.nullable() })
  .refine(value => value.dot <= value.text.length && value.mark <= value.text.length);
const citation = z.strictObject({ number: positive, title: text, location: text, excerpt: text });
const evidence = z.enum(['GENERAL', 'INLINE', 'RETRIEVED', 'INVALID', 'AMBIGUOUS', 'CONFIGURATION']);
const body = z.discriminatedUnion('kind', [
  z.strictObject({ kind: z.literal('stream'), phase: text, text }),
  z.strictObject({ kind: z.literal('answer'), raw: text, evidence, citations: z.array(citation) }),
  z.strictObject({ kind: z.literal('practice'), raw: text, evidence, citations: z.array(citation), exercises: z.array(z.strictObject({ question: text, explanation: text })).length(3) }),
  z.strictObject({ kind: z.literal('test'), raw: text, code: text.nullable(), source: text, target: text, problem: text.nullable() }),
  z.strictObject({ kind: z.literal('commit'), candidates: z.array(text), diff: text, chars: number, batches: number, calls: number }),
  z.strictObject({ kind: z.literal('failed'), message: text, raw: text }),
  z.strictObject({ kind: z.literal('stopped'), text }),
]);
const availability = z.strictObject({ enabled: z.boolean(), reason: text });
const card = z.strictObject({ at, mode, user: text, body, retry: availability, writeTest: availability, fillCommit: availability, generateTests: availability.nullable() });
const route = z.discriminatedUnion('kind', [
  z.strictObject({ kind: z.literal('chat') }),
  z.strictObject({ kind: z.literal('files') }),
  z.strictObject({ kind: z.literal('settings'), edit: id }),
  z.strictObject({ kind: z.literal('testPreview'), at, tab: z.enum(['code', 'raw']) }),
  z.strictObject({ kind: z.literal('sourcePreview'), title: text, location: text, excerpt: text }),
]);
const reading = z.strictObject({ following: z.boolean(), anchor: z.strictObject({ turn: id, offset: z.number().finite() }).nullable(), unseen: number, expanded: z.array(id) });
const provider = z.strictObject({ revision: number, phase: z.enum(['loading', 'ready', 'saving', 'clearing', 'failed']), endpoint: text, model: text, key: z.enum(['absent', 'bound', 'otherOrigin']), warning: text, settledForm: number.nullable() });
const shell = z.strictObject({
  mode, route, drafts: z.strictObject({ ask: draft, drill: draft, tests: draft, commit: draft }), generation: at.nullable(), provider,
  files: z.array(z.strictObject({ id, version: number, title: text, location: text, count: number, unit: z.enum(['pages', 'slides', 'sections']) })),
  fileTask: z.strictObject({ id, phase: text, cancellable: z.boolean() }).nullable(), fileWarnings: z.array(text), reading,
  theme: z.strictObject({ dark: z.boolean(), reducedMotion: z.boolean(), font: text, fontPx: z.number().positive().max(100), scale: z.number().positive().max(10) }),
  notice: text, confirm: z.strictObject({ id, text }).nullable(),
});
const change = z.discriminatedUnion('kind', [
  z.strictObject({ kind: z.literal('reset'), processed: number, shell, turns: z.array(id).max(40) }),
  z.strictObject({ kind: z.literal('shell'), shell }),
  z.strictObject({ kind: z.literal('card'), card }),
  z.strictObject({ kind: z.literal('text'), at, offset: number, text }),
  z.strictObject({ kind: z.literal('evict'), turns: z.array(id) }),
  z.strictObject({ kind: z.literal('hydrated') }),
  z.strictObject({ kind: z.literal('ack'), cseq: positive, result: z.discriminatedUnion('kind', [z.strictObject({ kind: z.literal('ok') }), z.strictObject({ kind: z.literal('rejected'), code: z.enum(['stale', 'invalid', 'busy', 'failed']), message: text })]) }),
]);
const host = z.strictObject({ v: z.literal(1), document: id, session: id, hseq: positive, change });

export function parseHost(value: unknown): Host | null {
  const result = host.safeParse(value);
  return result.success ? result.data : null;
}

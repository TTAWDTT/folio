export type Mode = 'ask' | 'drill' | 'tests' | 'commit';
export type Attempt = { turn: string; number: number };
export type DraftValue = { text: string; dot: number; mark: number };
export type Draft = DraftValue & { revision: number; epoch: number; context: string | null };
export type Citation = { number: number; title: string; location: string; excerpt: string };
export type Evidence = 'GENERAL' | 'INLINE' | 'RETRIEVED' | 'INVALID' | 'AMBIGUOUS' | 'CONFIGURATION';
export type Body =
  | { kind: 'stream'; phase: string; reasoning?: string; text: string }
  | { kind: 'answer'; raw: string; evidence: Evidence; citations: Citation[] }
  | { kind: 'practice'; raw: string; evidence: Evidence; citations: Citation[]; exercises: { question: string; explanation: string }[] }
  | { kind: 'test'; raw: string; code: string | null; source: string; target: string; problem: string | null }
  | { kind: 'commit'; candidates: string[]; diff: string; chars: number; batches: number; calls: number }
  | { kind: 'failed'; message: string; raw: string }
  | { kind: 'stopped'; text: string };
export type Availability = { enabled: boolean; reason: string };
export type Card = {
  at: Attempt; mode: Mode; user: string; body: Body;
  retry: Availability; writeTest: Availability; fillCommit: Availability; generateTests: Availability | null;
};
export type Route =
  | { kind: 'chat' }
  | { kind: 'files' }
  | { kind: 'settings'; edit: string }
  | { kind: 'testPreview'; at: Attempt; tab: 'code' | 'raw' }
  | { kind: 'sourcePreview'; title: string; location: string; excerpt: string };
export type Reading = { following: boolean; anchor: { turn: string; offset: number } | null; unseen: number; expanded: string[] };
export type Provider = {
  revision: number; phase: 'loading' | 'ready' | 'saving' | 'clearing' | 'failed';
  endpoint: string; model: string; key: 'absent' | 'bound' | 'otherOrigin'; warning: string;
  settledForm: number | null;
};
export type FileRow = { id: string; version: number; title: string; location: string; count: number; unit: 'pages' | 'slides' | 'sections' };
export type Shell = {
  mode: Mode; route: Route; drafts: Record<Mode, Draft>; generation: Attempt | null;
  provider: Provider; files: FileRow[]; fileTask: { id: string; phase: string; cancellable: boolean } | null;
  fileWarnings: string[]; reading: Reading;
  theme: { dark: boolean; reducedMotion: boolean; font: string; fontPx: number; scale: number };
  notice: string; confirm: { id: string; text: string } | null;
};
export type Credential = { kind: 'keep' } | { kind: 'replace'; value: string } | { kind: 'remove' };
export type Command =
  | { kind: 'edit'; mode: Mode; base: number; epoch: number; value: DraftValue }
  | { kind: 'send'; mode: Mode; base: number; epoch: number; value: DraftValue }
  | { kind: 'stop'; at: Attempt }
  | { kind: 'mode'; mode: Mode }
  | { kind: 'removeContext'; mode: Mode; revision: number }
  | { kind: 'route'; to: 'chat' | 'files' | 'settings' }
  | { kind: 'retry'; at: Attempt }
  | { kind: 'copy'; at: Attempt; part: 'user' | 'answer' | 'code'; index: number }
  | { kind: 'citation'; at: Attempt; index: number }
  | { kind: 'generateTests'; at: Attempt }
  | { kind: 'previewTest'; at: Attempt; tab: 'code' | 'raw' }
  | { kind: 'writeTest'; at: Attempt }
  | { kind: 'deliverCommit'; at: Attempt; index: number; delivery: 'copy' | 'fill' }
  | { kind: 'openSource' | 'reindex' | 'removeSource'; id: string; version: number }
  | { kind: 'saveSettings'; edit: string; providerRevision: number; formRevision: number; endpoint: string; model: string; credential: Credential }
  | { kind: 'cancelSettings'; edit: string }
  | { kind: 'chooseFiles' }
  | { kind: 'cancelFileTask'; id: string }
  | { kind: 'newChat' }
  | { kind: 'confirm'; id: string; accept: boolean }
  | { kind: 'reading'; value: Reading };
export type Client =
  | { v: 1; document: string; kind: 'ready' }
  | { v: 1; document: string; session: string; cseq: number; kind: 'command'; command: Command };
export type Change =
  | { kind: 'reset'; processed: number; shell: Shell; turns: string[] }
  | { kind: 'shell'; shell: Shell }
  | { kind: 'card'; card: Card }
  | { kind: 'text'; at: Attempt; offset: number; text: string }
  | { kind: 'evict'; turns: string[] }
  | { kind: 'hydrated' }
  | { kind: 'ack'; cseq: number; result: { kind: 'ok' } | { kind: 'rejected'; code: 'stale' | 'invalid' | 'busy' | 'failed'; message: string } };
export type Host = { v: 1; document: string; session: string; hseq: number; change: Change };

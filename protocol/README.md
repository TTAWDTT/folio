# Folio browser contract v1

`folio-v1.ts` defines the complete messages. Java uses strict allowlisted codecs for the same shapes. Shared valid and invalid JSON fixtures must check both codecs. No schema generator or generic action registry is needed.

The Java session owns workflow identity, drafts, captured requests, task acceptance and protected effects. React owns presentation, pending editor input, local form input, focus, selection and reading position. The JCEF adapter owns transport trust, document lifecycle, packaged assets and theme observation. No hidden Swing panel remains.

## Transport

The trusted page receives `window.folioHost`, containing `document` and `postMessage(json)`. It installs a `folio:host` event listener before posting ready. Host messages arrive as the event's object-valued `detail`. No other global entry point is needed. The production host provides the object through a bundled boot resource before the module entry.

Each new main document has a fresh unpredictable document identity. The browser origin is unique to that browser and serves only packaged assets. Ready causes reset, retained cards in order and hydrated. Controls enable only after hydrated. A reset establishes the host sequence baseline. Subsequent messages must be contiguous. On a gap, request ready without replaying commands. A fresh document starts cseq at one. Same-document ready retains processed cseq.

Commands use strictly increasing cseq and are admitted once on the EDT. A duplicate may acknowledge its previous admission but never repeat its effect. Reject gaps, stale documents, wrong sessions, extra fields, malformed shapes, invalid numeric ranges and oversized messages. Acknowledge admission promptly. Never keep Stop waiting for file dialogs, provider saves or Git checks. Report asynchronous settlement through state updates.

Use full hydration only for ready/recovery. Structural changes replace the changed card. Streaming appends use attempt plus exact text offset, followed by authoritative terminal replacement. Never resend unchanged full history per text delta. Retain at most 40 turns total. Eviction removes its request/result references and closes a preview that refers to it. Conversation keeps its separate existing context limits.

## Drafts and gestures

`epoch` changes only when an IDE entry replaces text/context. `revision` advances on accepted edits and replacements. An edit/send must match both. Java never silently rebases browser input onto a newly captured source. A conflicting browser buffer remains visible with a discard/reapply choice. Reapply requires an explicit user gesture. A normal acknowledgement cannot replace a newer pending buffer.

Send captures its mode, epoch, revision and text at press time. Flush preceding edits before admitting that captured Send. Stop captures the exact attempt at press time. Completion before release cannot turn Stop into Send. Enter sends unless composing; Shift+Enter inserts a newline. Preserve per-mode selection and drafts across routes. Reading anchor offsets are signed finite CSS pixel values and may be fractional. Native scenario-dialog prefill reads the latest acknowledged draft; a pending edit remains in the browser and is not silently replaced when the native action returns.

## Operations

All attempt operations resolve the retained Java request/result by exact turn and attempt. Copy uses the original Java raw text or exact raw fenced-code substring, including CRLF and trailing newline. Candidate copy/fill uses `deliverCommit` and the existing staged verification, never generic answer-copy. The browser supplies no code, file path or commit message to protected effects. `generateTests` acts on a completed model-backed Ask and reuses that request's captured Java source and target. It preserves an existing Tests scenario or derives one from the original question. It does not require an answer fence or interpret model output as source. Card `generateTests` is null when that action is inapplicable. A busy generation disables the eligible action, and Java rejects it before changing any draft. `citation` uses a retained citation index. Source operations use current library identity and revision.

`writeTest` retains accepted-result, source stamps, target, PSI, package/collision/writability, project-open and Undo checks. `deliverCommit` retains background and final staged verification and the original commit control. Recheck the document lease immediately before clipboard, fill, write or deferred chooser effects. Generation never automatically performs any of these effects.

Route cancellation discards settings edits. Credential Keep/Replace/Remove is explicit and applies only on Save. Saved credentials never enter the browser. Newly typed keys cross only explicit saveSettings and are never logged, persisted or replayed. `settledForm` acknowledges the exact saved form revision; completion cannot clear a newer edit. Current endpoint/model and origin-bound credentials are captured on every attempt, including retries. Redact backend errors before projection.

File import/reindex remains atomic and shared across IDE projects. Keep independent FILES/SOURCE task channels, task cancellation and warning visibility. Removal and populated New chat require Java-owned confirmation. New chat preserves unsent drafts.

## UI and proof

The IDE owns the only Folio title. The web UI has one compact utility row with new-chat, files and settings icons. Auxiliary back/title controls occupy the same row. All icons have accessible names and hover text. Copy/retry contain icons only. Clear the replaced native utility actions.

Use real HeroUI v3 components with React 19, Tailwind 4 and HeroUI styles. Dropdown owns its open state. Its popup opens above the whole composer, including in a 280px sidebar. Tooltips/popovers must not move underlying controls. Textarea grows to six lines; only code and textarea interiors may scroll horizontally. Respect IDE light/dark, scale and reduced motion.

Parent-owned browser acceptance is in `web/e2e/sidebar.acceptance.spec.ts`. The fixture substitutes only the actual Java transport boundary. It is not native-JCEF proof and cannot prove Java effects. Keep controller and backend guard oracles as separate checks. Deleting Swing-only tests requires an explicit replacement-oracle map.

Required commands will be supplied by the frontend manifest. A nonzero syntax/type/test exit is failure. A browser run with zero tests or skipped acceptance is failure. The current test-first commit precedes that manifest and renderer, so it is not a reproduced regression or an executable UI pass.

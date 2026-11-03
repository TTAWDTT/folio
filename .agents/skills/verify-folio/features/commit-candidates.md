# Commit candidates

Folio drafts messages from the captured staged changes. A candidate reaches the clipboard or original commit box only after the user chooses that action.

## Sub-features

- `commit-entry` covers composer mode and the commit-dialog action.
- `commit-generate` covers no repository, empty staging, loading, provider failure, candidates, and captured diff details.
- `commit-deliver` covers explicit copy and fill, changed staging, changed commit box, and stale results.

## How to get to it (user POV)

- Choose **提交信息** in the composer and send an optional intent.
- Choose **Draft Folio Commit Messages** from the IDE commit dialog.
- Choose a candidate's copy action or its fill-original-commit-box action when the result has that captured target.

## Driving it with agent-desktop

Preconditions: the doctor passes. Use a disposable Git repository with known staged changes and an approved real provider. Record its current HEAD, staged diff, original commit text, and a non-private clipboard baseline.

- Use the mode-menu procedure to choose **提交信息**. Require an explicit explanation for no Git repository or no staged changes. In the staged fixture, send the intent and require the repository roots, staged file count, call estimate, captured diff, and candidate results.
- Use the commit-dialog action only when it preserves the user's focus constraint. Require the original commit target to remain unchanged after generation. Composer-only results without a captured commit box must not invent a fill target.
- Before choosing any candidate action, require the original clipboard, commit box, HEAD, and staging to remain unchanged. Record the native labels from the fresh tree. The integrated source names these actions **复制提交信息** and **填入提交框**. Recheck the live tree before targeting them.
- Choose explicit copy through the shared semantic procedure. Require the selected candidate in the disposable clipboard. Choose fill on a current result with an unchanged target. Require that candidate only in the original commit box. Require unchanged HEAD and staging after both operations.
- Generate again, then change staging or the original commit text in the disposable fixture. Choose the corresponding delivery action. Require refusal and preservation of the newer user state. Repeat with a superseded result when reachable.

## Gotchas

- Without a real provider, staged repository, and captured commit control, the generation and fill paths remain unverified.
- Record the actual number of returned candidates. A request for three candidates does not establish that the provider returned three.
- Headless staging tests do not prove native commit-dialog delivery. A visible success message does not prove that the original target received the text.
- Never execute a Git commit or change the user's staging to verify candidate delivery.

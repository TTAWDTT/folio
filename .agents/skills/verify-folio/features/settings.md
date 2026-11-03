# Settings and return

Settings opens a separate page. Cancel and back preserve the saved connection and the current conversation.

## Sub-features

- `settings-return` covers both **取消** and **返回对话**, with an existing draft and conversation.
- `settings-edit` covers an unsaved model edit and deferred credential removal.
- `settings-save` covers valid save, field validation errors, persistence failure, and a blank key that retains the saved credential.
- `settings-key` covers a saved-key placeholder and visibility of a newly typed key.

## How to get to it (user POV)

- Choose the header icon named **设置**.
- Choose **去设置** from the missing-model message when that message is present.

## Driving it with agent-desktop

Preconditions: the doctor passes. Capture the existing conversation, mode, draft, context, and visible reading anchor. The cancel journey needs no provider and changes no saved values.

- Open settings through each available entry point. Find the header target with `agent-desktop find --root "$FOLIO_ROOT_REF" --role button --name "设置" --exact --limit 2`. Failure means a nonzero exit, `ok: false`, or a match count other than one. Click the returned ref through the shared procedure. Require **API 地址**, **模型**, **API Key**, **保存并返回**, **取消**, and **返回对话** on the auxiliary page.
- Choose **取消**. Require the original conversation and draft state. Repeat with **返回对话**. Capture each result separately.
- In a disposable settings profile, use semantic `set-value` on the model field to enter `folio-verification-unsaved`. Cancel, reopen, and require the saved model. Repeat with back. If field identity or value write is unavailable, retain that refusal.
- In the disposable profile, mark **移除已保存的 Key**, then cancel. Require that the saved credential remains usable for its bound origin. Do not reveal its value. A label alone cannot prove credential persistence.
- For save coverage, use an approved disposable provider profile. Require invalid fields to retain the page with nearby errors. Require a successful save to survive reopening. Record external connectivity separately.
- For key visibility, enter only a disposable test value. Require the eye action to affect that new value. Require a saved credential to remain absent from the field.

## Gotchas

- The safest initial native journey is settings, cancel, settings, back. It does not establish save, Password Safe, or connectivity behavior.
- Reading position and caret coverage remain gaps if the native observation cannot identify them.
- Never test key removal or save against the user's existing credential. Do not inspect or record secret field values.

# Conversation and modes

The composer retains a separate draft for each mode. Sending captures the request, and stopping preserves the received answer and next draft.

## Sub-features

- `chat-modes` covers closed and open menus, the current item, all four modes, and per-mode drafts.
- `chat-request` covers empty input, missing model configuration, loading, partial output, completion, failure, retry, and stop.
- `chat-read` covers long content, suspended following, selection, and **回到最新**.
- `chat-actions` covers icon-only copy and retry, copy feedback, disabled reasons, and new-conversation confirmation.
- `chat-practice` covers three exercises and independent explanation disclosures.

## How to get to it (user POV)

- Open the **Folio** tool window and use **选择模式**. The choices are **问答**, **练习**, **生成测试**, and **提交信息**.
- Use **Ask Folio** in the editor context menu for selected code.
- Use **Folio Course Drill** in the editor context menu for a Java class, method, or selection.

## Driving it with agent-desktop

Preconditions: the doctor passes. Use a disposable conversation for sends or resets. A real provider is required for generated answers and exercises.

- Find the mode button with `agent-desktop find --root "$FOLIO_ROOT_REF" --role button --name "选择模式" --exact --limit 2`. Failure means a nonzero exit, `ok: false`, or a match count other than one. Activate its fresh ref, then observe the menu surface. Require exactly the four choices. Select the current item semantically when exposed. Require a closed menu and unchanged mode and draft. Reopen and select another item. Require the new mode and a closed menu.
- Enter distinct unsent drafts through exposed value writes. Switch modes and return. Require each draft and its attached context to return without changing history. In **问答** without attached code, empty input must create no task. A long draft must stop growing after six rows and scroll inside the composer.
- Send through the current send control. Require one user row and one task. While output arrives, enter a next draft. Stop through the same control. Require the partial answer and next draft to remain. Confirm that late output does not resume the task.
- For failure and retry, use a disposable provider setup that can produce the relevant state. Require **重新生成** to reuse the failed request without duplicating the user row or replacing the next draft.
- Exercise both editor entry points only when their activation preserves the user's focus constraint. Require the selected code to appear as the captured context. Practice must display three questions. Expand one **展开解析** control and require the other two explanations to stay closed.
- In a populated conversation, inspect answer actions named **复制回答** and **重新生成**. An explicit copy must change only the intended clipboard value and show local feedback. Generation must not copy automatically. New conversation must ask before clearing populated history. Cancel must retain it.
- For reading, use an exposed semantic scroll action. Require incoming output to preserve the reading anchor. **回到最新** must restore following. Record selection preservation only when selection and incoming output are both observable.

## Gotchas

- A semantic click does not prove the original physical press, popup dismissal, and release order. The repeated-toggle, outside-click, Esc, Enter, Shift+Enter, IME, and hover journeys remain unverified under the no-physical-input rule.
- An editor action can activate the tool window. Do not invoke it if that takes focus from the user.
- A missing provider or suitable response leaves loading, streaming, retry, and three-exercise behavior unverified. Never replace a real-path proof with an internal panel setter.
- Clipboard changes need a disposable clipboard baseline. Do not overwrite private user clipboard contents merely to complete this map.

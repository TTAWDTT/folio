# Test previews and writes

Generated tests appear in a separate preview. Only an explicit write creates the shown target file after validation.

## Sub-features

- `tests-entry` covers the editor scenario dialog, mode entry, and a completed answer's test action.
- `tests-preview` covers loading, generation failure, invalid Java, valid Java, and an unbound target.
- `tests-write` covers successful creation, existing filename, changed source, changed preview, and unwritable destination.
- `tests-return` covers returning to the original conversation and draft.

## How to get to it (user POV)

- Choose **Generate JUnit From Scenario** in the editor context menu for a Java class or method.
- Choose **生成测试** in the composer after attaching Java context.
- Choose **生成测试** under an eligible completed code answer.
- Choose **查看测试预览**, inspect **Java 预览** and **原始回复**, then choose **写入文件**.

## Driving it with agent-desktop

Preconditions: the doctor passes. Use a disposable Java project with an identifiable source, a known test directory, and an approved real provider. Record the initial target-directory inventory.

- Reach each available entry point using exposed semantic controls. Cancel the scenario dialog and require no generation. Empty acceptance must retain a missing-scenario explanation. If opening an editor action would take focus, record the entry point as unverified.
- After generation, find **查看测试预览** on the identified JCEF page. A missing or ambiguous button is failure. Click it and require the source, target path, code, raw reply, and validation result.
- Before choosing **写入文件**, require no new test file on disk. Return with **返回对话** and require the original conversation and draft. Reopen the current preview.
- For a valid bound preview, choose **写入文件** only in the disposable project. Require the exact displayed file to exist with the preview contents. Require no second model request. Inspect the file through the IDE and run that disposable project's test command before claiming the generated test executes.
- Repeat with an existing target, changed captured source, superseded preview, and read-only destination. Require refusal with the preview retained and no overwrite. Invalid or unbound previews must remain inspectable without a write.

## Gotchas

- PSI syntax and recognized JUnit annotations do not prove compilation, discovery, execution, or test quality.
- A headless `GeneratedTest` fixture does not prove the native preview-to-write journey.
- No provider or suitable source means the generation path is unverified. Do not insert a preview through an internal setter to remove that gap.
- Reconcile **写入文件** with the integrated source and live tree before using it as an exact target.

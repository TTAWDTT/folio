# Course files and sources

The folder opens the shared course library. Imports preserve usable old data on failure, and answer references open the cited source.

## Sub-features

- `files-return` covers opening and returning with an unchanged conversation.
- `files-list` covers loading, empty, one source, many sources, long names, and import errors.
- `files-mutate` covers cancelled import, new import, reindex, and confirmed or cancelled removal.
- `files-source` covers collapsed references, expanded excerpts, file navigation, and source preview fallback.

## How to get to it (user POV)

- Choose the header folder named **课件**. Choose **返回对话** to return.
- Choose **导入课件** in the empty state or populated file toolbar.
- Choose a row's **更多文件操作** for **重新索引** or **移除**.
- Expand **展开课件引用** below a retrieved answer, then choose the source title.

## Driving it with agent-desktop

Preconditions: the doctor passes. Navigation uses the existing library. Mutations need a disposable shared-library profile and known synthetic course files. Use `src/test/resources/cst3492/courseta/index/retrieval-pages.tsv` as a source of synthetic text, not as an imported course format.

- Find the folder with `agent-desktop find --root "$FOLIO_ROOT_REF" --role button --name "课件" --exact --limit 2`. Failure means a nonzero exit, `ok: false`, or a match count other than one. Click its fresh ref. Require the shared-library notice and separate course page. Choose **返回对话** and require the original conversation state.
- In the disposable profile, start with no sources. Require the import prompt. Import one text fixture, then several fixtures including a long filename. Require source names, scope counts, and indexed rows without clipped controls. Reopen the page and confirm the same library entries.
- Cancel the file chooser. Require no library change. Import a missing or unreadable fixture through the normal selection path when supported. Require a nearby failure and the old library. Do not bypass the chooser with an internal import method.
- Reindex one row through **更多文件操作**. Require one updated source rather than duplicate rows. Cancel removal and require the source. Confirm removal of a disposable source and require only that library entry to disappear. Require the original course file to remain on disk.
- Ask a local retrieval question without a key in the disposable profile. Require the missing-model explanation and retrieved references. Expand the references, select an excerpt, and activate only its source title. Text sources open at the excerpt. Non-text or missing sources use the indexed preview. Require the answer and reading anchor to remain.

## Gotchas

- Materials are shared across projects in one IDE configuration. A disposable project alone does not isolate this library.
- If the profile lacks sources or the native chooser lacks semantic controls, record that unmet precondition. Navigation-only proof does not cover import or retrieval.
- Source navigation may activate an editor. Leave that path unverified if it would take the user's focus.
- Reconcile **导入课件** with the integrated source and live tree before using it as an exact target.

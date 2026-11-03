# Folio verification map

Read [the skill](../SKILL.md) before driving. Each entry describes expected behavior, not a passing result.

## Preconditions and proof

- Confirm the live sandbox and installed plugin identity before every run. Reuse an existing owner of the sandbox.
- Start from the current user state for navigation checks. Use disposable projects and data for mutations.
- Use fresh qualified refs for native controls, or DOM roles on the identified JCEF page. Capture the action, resulting state, and screenshot.
- Use the shared `Drive` procedure for every target below. A missing semantic action leaves that path unverified.
- Record every listed entry point and state separately. One mapped native journey validates the skill's recipe, not the whole feature map.
- Keep native pointer, keyboard, hover, provider, and actual-write gaps separate from headless tests.

## Features

- [Conversation and modes](conversation.md) covers chat, practice, requests, drafts, and reading.
- [Settings and return](settings.md) covers draft settings, cancel, save, and credentials.
- [Course files and sources](course-files.md) covers shared materials, import, file actions, and citations.
- [Test previews and writes](test-preview.md) covers editor actions, preview, validation, and explicit file creation.
- [Commit candidates](commit-candidates.md) covers both entry points, staging snapshots, and explicit delivery.

## Source and label boundary

The map retains the established workflows through the HeroUI migration. Its primary contracts are [approved.md](../../../../docs/design/sidebar-v3/approved.md), [icon-spec.md](../../../../docs/design/sidebar-v3/icon-spec.md), and [the browser protocol](../../../../protocol/README.md).

`FolioView.Mode` defines the four modes. `FolioSession` owns requests and workflows. `FolioBrowser` hosts the production page, and `web/src` owns presentation. [plugin.xml](../../../../src/main/resources/META-INF/plugin.xml) defines the editor and commit-dialog entry names. The feature files describe outcomes; use the skill's DOM procedure for the HeroUI page.

The integrated source uses **导入课件** and **写入文件**. Recheck the live accessibility tree before using either exact target.

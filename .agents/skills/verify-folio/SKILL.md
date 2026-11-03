---
name: verify-folio
description: Verify the Folio IntelliJ Swing sidebar after layout, mode-menu, settings, course-file, test-preview, or commit-candidate changes. Use for native sandbox walkthroughs and evidence capture without moving the system pointer or taking focus.
---

# Verify Folio in IntelliJ

Use this skill to check the real **Folio** tool window. Read the [feature map](features/README.md), then select the affected entries.

This skill is a draft until one mapped feature completes a native launch-or-reuse, doctor, drive, evidence, and cleanup run. Source inspection and headless Swing tests do not complete that run. Keep the native proof in a separate run record.

## Launch

1. Work from the repository root. Record the source revision and dirty paths. Read [README.md](../../../README.md) for JDK 25 and the current launch requirements.
2. Discover an existing IntelliJ sandbox before starting anything. Reuse the user-owned instance when it owns this checkout's sandbox. Never start two IDEs against one configuration, system, or plugin directory.
3. When no instance owns the sandbox and the task permits a new IDE launch, use the documented command below. If a launch would violate the no-focus rule, report that launch prerequisite as unavailable.

Failure signal: a nonzero exit means the sandbox launch failed.

```sh
./gradlew runIde
```

The default sandbox is `.intellijPlatform/sandbox/folio/IU-2026.2.4/`. Inspect the live JVM arguments to confirm `config_runIde`, `plugins_runIde`, and `log_runIde`. Read `log_runIde/idea.log` for `Folio tool window created`, then require a readable Folio sidebar with its composer and header. A process or log line alone does not establish readiness.

Use an existing project for navigation-only checks. Use a disposable project and disposable settings or course-library state for write checks. A fresh profile needs no key for local retrieval. It needs imported materials for citations and a real configured provider for generated content.

## Doctor

Use the **agent-desktop** skill and the **agent-desktop-macos** skill. Read their observation and interaction references before driving. Run these read-only checks whenever identity or responsiveness is uncertain.

Failure signal for each command below: a nonzero exit or `ok: false`. Missing Accessibility or Screen Recording permission prevents the relevant check. Do not open a permission guide during a no-focus run.

```sh
agent-desktop status
agent-desktop list-windows --app Main
```

Choose the window whose title and project match the intended sandbox. Set `FOLIO_WINDOW_ID` and `FOLIO_PID` from that fresh result. Never reuse a window ID or PID from a previous run.

Failure signal: a missing process or JVM arguments pointing at another sandbox means the instance is unsuitable.

```sh
ps -p "$FOLIO_PID" -o pid=,command=
```

Failure signal: a nonzero exit, `ok: false`, or no readable Folio subtree. An incomplete tree requires a narrower drill before using its missing nodes as evidence.

```sh
agent-desktop snapshot --app Main --window-id "$FOLIO_WINDOW_ID" --skeleton -i --compact
```

Record the installed plugin's build identity alongside the source revision and current plugin ZIP checksum. A ZIP built from the current source does not prove that an old JVM loaded it. Stop claims about changed behavior until installation or reload evidence identifies the running plugin.

## Drive

The system pointer and focus belong to the user. Never use `--headed`, `press`, `hover`, `focus`, `focus-window`, physical typing, mouse commands, coordinates, or an API that activates the IDE. Never recover a failed semantic action with physical input.

1. Drill into the Folio region with `snapshot --root` when the skeleton cuts it off. Set `FOLIO_ROOT_REF` to its fresh addressable ancestor.
2. Find a target by its exact accessible name within that region. Set `FOLIO_TARGET_REF` to the single returned qualified ref. When several controls share a name, narrow to the freshly identified header or input region and repeat the search. A remaining ambiguous match is an evidence gap.
3. Use headless `click` for an exposed activation and `set-value` for an exposed value write. Use semantic selection, expansion, or scrolling only when the element supports that action.
4. Capture the action response and a fresh tree and screenshot after every state change. Replace stale refs. If delivery is uncertain, observe the live state before any retry.

Failure signal for each command below: a nonzero exit or `ok: false`. A click response alone does not prove the page changed. A value write proves only the field value until the resulting application state is observed.

```sh
agent-desktop find --root "$FOLIO_ROOT_REF" --role button --name "设置" --exact --limit 2
agent-desktop click "$FOLIO_TARGET_REF"
```

For a draft edit, first identify the editable field in a fresh tree and replace `FOLIO_TARGET_REF` with its ref. Never target a button or saved credential.

Failure signal: a nonzero exit, `ok: false`, or an observed field value that differs from the requested value.

```sh
agent-desktop set-value "$FOLIO_TARGET_REF" "folio-verification-unsaved"
```

If Swing exposes no semantic action, retain the refusal and report the path as unverified. The 2026-11-03 installed sandbox returned `ACTION_NOT_SUPPORTED` with `window_without_accessibility_element` after restart. Screenshots worked, but no native journey completed. Do not treat a screenshot-only run as this skill's validation.

Native pointer toggle order, outside clicks, hover timing, keyboard shortcuts, IME composition, and keyboard focus require separate proof. Under the current input restriction, leave them unverified. Synthetic Swing event tests remain supporting evidence.

## Evidence

Create `.audit/verify-folio/<UTC run ID>/` under the repository root before a run. Set `FOLIO_EVIDENCE` to that absolute directory. Keep the run record, command exits, JSON responses, and screenshots there. Do not commit credentials, private course text, or user-owned drafts.

For every feature ID and entry point, retain the before state, action, after state, and expected result. Record each listed state as passed, failed, or unverified with a reason. Capture the actual write or its absence alongside visible feedback.

Failure signal for each command below: a nonzero exit, `ok: false`, or a missing or empty output artifact. Discover the window ID again if the window changes. Prefer that ID over an app-only screenshot when window inventory changes.

```sh
agent-desktop snapshot --app Main --window-id "$FOLIO_WINDOW_ID" -i --compact > "$FOLIO_EVIDENCE/after-tree.json"
agent-desktop screenshot --window-id "$FOLIO_WINDOW_ID" "$FOLIO_EVIDENCE/after.png"
```

Compare the installed sidebar with [approved.md](../../../docs/design/sidebar-v3/approved.md) and [01-chat-icons.png](../../../docs/design/sidebar-v3/01-chat-icons.png). Record the theme and available sidebar width. A static design image or offscreen render does not prove native interaction.

When source or build verification is in scope, use the repository's existing check below. Read the measured results and ZIP checksum in `build/verification/summary.json`. Never copy a previous test count.

Failure signal: a nonzero exit, missing summary, unsuccessful suite, or invalid plugin archive.

```sh
./scripts/verify.sh
```

Report a real provider call, endpoint reachability, campus-network reachability, and answer quality separately. A saved setting or loopback HTTP fixture does not prove any of them.

## Cleanup

Return through the page's **取消** or **返回对话** semantic action when available. Confirm the original conversation, mode, draft, context, and reading position remain. Do not reset the user-owned conversation or shared course library to manufacture a clean baseline.

Leave a reused IDE running. For an IDE created by this run, stop only its recorded process after checking its identity and resolving save prompts without physical input. Never kill by process name. Remove only disposable state created by the run.

After every failed attempt, apply the same cleanup. Confirm the evidence directory still contains the command record, trees, and screenshots after cleanup. Keep unresolved state changes in the run record.

## Helpers and maintenance

This skill ships no helper. `scripts/verify.sh` is the existing build check. Use `/maintain-verification-skill` when routes or accessible names change.

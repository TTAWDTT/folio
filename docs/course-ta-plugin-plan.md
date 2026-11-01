# Folio plugin plan

Group B builds Folio, an IntelliJ IDEA plugin that answers from course files and adds three smart actions. The team demos that plugin in the IDE. The program covers F0 and F1 and does not build F2. PR order is PR-1, PR-2, PR-3, PR-4, PR-5, then PR-6.

Read the Chinese copy at [course-ta-plugin-plan.zh.md](course-ta-plugin-plan.zh.md). The checker still reads this English file.

## How to read this

One box is one unit of work. Every box names the evidence that checks it. A nested box is a sub-step of the box above it. Check a box only when its evidence exists, a file, a log line, a screenshot, a test run, or a SHA. The body is a how-to. The appendices explain and record.

The program runs `playbooks/autopilot-stack.md`. The operator lands every PR. Owners stop at merge-ready. PR-1 through PR-6 are the operator's items that stop at merge-ready.

Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

## Program checklist

### Arm the program

- [ ] State the protocol and this plan to the operator, then stop. Start execution only on the operator's explicit go.
- [ ] Read these from trunk at program start. Re-read them at every tick.
  - [ ] `git show origin/main:pstack/skills/poteto-mode/playbooks/autopilot-stack.md`
  - [ ] `git show origin/main:pstack/skills/swarm/SKILL.md`
  - [ ] `git show origin/main:cursor-team-kit/skills/control-ui/SKILL.md`
  - [ ] `git show origin/main:pstack/skills/poteto-mode/playbooks/opening-a-pr.md`
  - [ ] `git show origin/main:pstack/skills/poteto-mode/playbooks/prototype.md`
- [ ] On the operator's go, arm the audit tick as `/loop 1h` with the tick prompt below. Never leave the cadence to memory.
- [ ] Use this tick prompt, verbatim. "Re-read the execution playbook from trunk. Audit the operation against it and fix drift in this tick. Probe every active lane and judge progress by side effects only. Stand down a stuck lane and dispatch its replacement now. Then post a short status message to the operator in chat only when the audit found a tracked change that no earlier status message reported, such as a PR opened, a code-ready head, a round launched or closed, a verdict, a merge, a stuck agent and the action taken, a blocker added or cleared, or a decision only the operator can make. Name every such change and nothing else. Do not repeat a table, the merged list, or an unchanged blocker. If the audit found none, end the turn with no reply text. Either way, log this tick's row in your decision trail. The row names the items reported, or none."
- [ ] On the operator's hold or stand-down, send every owner a zero-writes order at once.

### Spawn owners

- [ ] Spawn one owner per PR with the full lifecycle the execution playbook names.
- [ ] Follow this dependency graph. Start dependent work only after its parent exists on a branch, and base each child on that parent branch.
  - [ ] PR-1 is first and branches from `main`.
  - [ ] PR-2 after PR-1.
  - [ ] PR-3 after PR-2.
  - [ ] PR-4 after PR-3.
  - [ ] PR-5 after PR-4.
  - [ ] PR-6 after PR-5.
- [ ] Hold the file boundaries. Each PR touches only the paths in its **Files.** block, plus `src/main/resources/META-INF/plugin.xml` when it registers one new action or one new tool window.
- [ ] Hold the review gate. PR-1, PR-2, PR-3, PR-4, PR-5, and PR-6 change an interaction. They wait for the operator's review in chat with screenshots and a video before merge.

### PR mechanics, for every PR

- [ ] Resolve the forge once. Default to `gh`. If `command -v origin` succeeds and Origin can resolve the repository, use `origin pr` for every PR operation. Record any fallback to `gh`. Never require `gt`.
- [ ] Open the PR ready, never draft, per **Opening a PR**. Use the run's built-in PR tool when it has one, else `origin pr create --status open --base <base-branch>` or `gh pr create --base <base-branch>` according to the resolved forge. A stack child targets its parent branch.
- [ ] Run the repo's lint and typecheck once before the PR-facing push. Push with hooks on.
- [ ] Run `/deslop` before each commit and `/no-comments` before review.
- [ ] Triage every Bugbot and security-reviewer comment per `../references/bugbot-triage.md`.
- [ ] Rebase onto the parent branch before the code-ready report and babysit. The stack root rebases onto current trunk. Keep that merge base in fix rounds. Rebase again only at merge prep, on a `git merge-tree` conflict with the parent, on a CI failure that comes from a change on the parent, or when a known concurrent change to the same files lands.

### Verdict and merge, for every PR

- [ ] A full swarm runs once, at the code-ready head. Another full swarm runs only for new behavior, a redesign, or a large new diff. A rebase, a merge-conflict resolution, or a minor fix gets a single verifier at the new head, and a wording-only change gets none. Run each full swarm per `pstack/skills/swarm/SKILL.md`. One gates lane. The ten live lanes from the PR's **Verify, live** block. The perf lane from its **Verify, perf** block. Two or more audit lanes, each with its own focus, that read the diff and the receipts and distrust the PR body. The root audits the receipts in the merge-ready report before the verdict.
- [ ] Judge every finding by the execution playbook's blocking rule. The verdict is clean when none blocks. A failure on trunk behavior in a lane marked `Regression lane against trunk.` is recorded against trunk and does not block the PR. A new head gets a fresh verdict, except for results that stay valid under the patch-id rule in `playbooks/shipping.md`.
- [ ] Stop when the PR is verified and green at its current head, then hand it to the operator. Do not merge. After the handoff, nobody pushes without the operator's go. A restack that leaves `git patch-id` unchanged keeps the verdict. The operator lands the stack bottom-up only after every PR in the chain has a clean verdict.

### Boot recipe, for every live lane

Each live lane runs on its own cloud VM at the PR head. This IDE has no `control-ui` or `control-cli` driver. Drive the headless predicate with `./gradlew test`. Drive the window by launching the sandbox with `./gradlew runIde` and capturing the named screenshot from that sandbox. Appendix C records the missing driver.

- [ ] `git fetch origin <head-branch> && git checkout <head SHA>`.
- [ ] Install JDK 25. Run `./gradlew test` and wait until the task prints `BUILD SUCCESSFUL` or a failing test name. For a window lane, run `./gradlew runIde` and wait until the log prints `Folio tool window created`.
- [ ] Deliver input through the Gradle tasks above. Read-only diagnostics are the test XML under `build/reports/tests` and the sandbox log. Do not type into the IDE from an unlogged click.
- [ ] Save every screenshot to `/tmp/swarm-<pr-id>/worker-<n>/<slug>.png` and return the paths with the report.

## Scaffold the plugin shell (PR-1)

**Depends on.** None.

**Files.**

- [ ] Create `settings.gradle.kts`.
- [ ] Create `build.gradle.kts`.
- [ ] Create `gradle/wrapper/gradle-wrapper.properties`.
- [ ] Create `src/main/resources/META-INF/plugin.xml`.
- [ ] Create `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`.
- [ ] Create `src/main/java/cst3492/courseta/TaSettings.java`.
- [ ] Create `src/test/java/cst3492/courseta/TaSettingsTest.java`.

**Build.**

- [ ] Apply plugin id `org.jetbrains.intellij.platform` version `2.19.0` in `build.gradle.kts`.
- [ ] Depend on `intellijIdea("2026.2.4")` and `bundledPlugin("com.intellij.java")`.
- [ ] Set `sinceBuild` to `262` and `untilBuild` to `262.*` with no product-code prefix.
- [ ] Resolve a Gradle 9 wrapper from the Gradle release page at the start of this PR. Do not invent the patch number in advance.
- [ ] Register `CourseTaToolWindowFactory` on `com.intellij.toolWindow` with id `Folio`.
- [ ] Store the base URL and the model name in `TaSettings` as a `PersistentStateComponent`. Store the API key only through `PasswordSafe` off the EDT.

**You see.**

- [ ] The sandbox shows a `Folio` tool window whose body says no course files are indexed.
- [ ] Settings show an empty base URL, an empty model name, and a key field that does not echo the stored key.

**Verify, unit.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] `TaSettingsTest` stores a model name and asserts the exported state XML has no API key. Run `./gradlew test`.

**Verify, live.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked. Ten lanes on `grok-4.7-xhigh-fast` at the PR head, per the boot recipe.

- [ ] Lane 1. Regression lane against trunk. Run the empty workspace at trunk and the sandbox at head. If trunk lacks the feature, record that and gate the `Folio` window plus the idle panel the user waits for. Save `pr1-trunk-absent.png`. Pass when the head shows `Folio` and trunk has no such window.
- [ ] Lane 2. Open the tool window from the tool-window stripe. Save `pr1-open-window.png`. Pass when the title is `Folio` and the body says no course files are indexed.
- [ ] Lane 3. Open settings. Save `pr1-settings-empty.png`. Pass when the base URL field, the model field, and the key field are visible and empty.
- [ ] Lane 4. Enter a base URL and a model name, restart the sandbox, and reopen settings. Save `pr1-settings-persist.png`. Pass when both values survived and the key field is still empty.
- [ ] Lane 5. Store a key, restart, and open the exported settings XML beside the settings page. Save `pr1-key-not-in-xml.png`. Pass when the XML has no key text and the page shows that a key is stored.
- [ ] Lane 6. Narrow the tool window. Save `pr1-narrow.png`. Pass when the settings control remains visible.
- [ ] Lane 7. Leave the key empty and look at the Ask control. Save `pr1-ask-disabled.png`. Pass when Ask is disabled and the hint names the missing key.
- [ ] Lane 8. Close the tool window and open it again. Save `pr1-reopen.png`. Pass when the idle copy is back.
- [ ] Lane 9. Run `./gradlew test` and open the HTML report. Save `pr1-test-report.png`. Pass when the task prints `BUILD SUCCESSFUL`.
- [ ] Lane 10. Read the sandbox log after startup. Save `pr1-plugin-loaded.png`. Pass when the log names the plugin and does not name a missing `com.intellij.java` dependency.

**Verify, perf.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] Metric. Seconds from the `runIde` process start until the log prints `Folio tool window created`, and the idle panel the user waits for. Trunk has no plugin, so record that trunk has no such log line.
- [ ] Probe. Start trunk first, then head, then trunk again. Read the factory log line on head. Record the absence on trunk.
- [ ] Baseline. Record the trunk absence first.
- [ ] Rule. Fail the head when that log line is absent, or when it appears more than 90 seconds after process start. Do not divide head by trunk. The 90 second ceiling is an unmeasured budget for a cold sandbox, not a timing this plan observed.

**Review gate.** The operator reviews before merge.

- [ ] Copy lane 2 screenshots. Save them as pr-1-review-window.png.
- [ ] Record a 30 to 60 second video of opening the tool window and the settings page on a lane VM. Save it as pr-1-review.mp4.
- [ ] Post the screenshots and the video in chat. Stop at merge-ready. Wait for the operator's review.

**Merge.**

- [ ] Root's clean verdict at the exact head SHA.
- [ ] Bugbot triage done.
- [ ] Rebased onto current trunk after the verdict, patch-id unchanged.
- [ ] The owner does not merge. The operator lands PR-1 when the stack is ready.

## Index course files (PR-2)

**Depends on.** PR-1.

**Files.**

- [ ] Create `src/main/java/cst3492/courseta/index/CourseChunk.java`.
- [ ] Create `src/main/java/cst3492/courseta/index/CourseIndex.java`.
- [ ] Create `src/main/java/cst3492/courseta/index/CourseFileParser.java`.
- [ ] Create `src/test/java/cst3492/courseta/index/CourseIndexTest.java`.
- [ ] Create `src/test/resources/fixture-two-page.pdf`.
- [ ] Edit `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`.
- [ ] Edit `build.gradle.kts`.

**Build.**

- [ ] Add `CourseChunk` with `id`, `sourceDocument`, `scopeKind` of `PAGE` or `SLIDE`, `scopeNumber` starting at 1, and `text`.
- [ ] Parse with Tika `4.2.0` modules `tika-core`, `tika-parser-pdf-module`, and `tika-parser-microsoft-module`. Pass the plugin class loader into `DefaultParser`. Set `includeSlideMasterContent` to false.
- [ ] Store one chunk per PDF `div` of class `page`, or per PPTX `div` of class `slide-content`. Drop empty text.
- [ ] Rank with an in-memory token map. Index ASCII words and character bigrams. Save the chunk map under the plugin config directory and load it on startup.
- [ ] Add an Index files button that copies the chosen files into the plugin corpus and rebuilds the map.

**You see.**

- [ ] After indexing the two-page fixture, the tool window lists two rows. Each row shows the file path and the page number.
- [ ] A Chinese query that shares a bigram with page 2 highlights that row and does not highlight an unrelated page.

**Verify, unit.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] `CourseIndexTest` indexes a fixture string pair and asserts the Chinese query returns only the matching chunk id. Run `./gradlew test`.

**Verify, live.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked. Ten lanes on `grok-4.7-xhigh-fast` at the PR head, per the boot recipe.

- [ ] Lane 1. Regression lane against trunk. Run indexing at trunk and at head. If trunk lacks the feature, record that and gate the two listed rows plus the idle panel the user waits for before indexing. Save `pr2-trunk-absent.png`. Pass when head lists two rows and trunk has no Index files button.
- [ ] Lane 2. Index `fixture-two-page.pdf`. Save `pr2-two-pages.png`. Pass when two rows show page 1 and page 2.
- [ ] Lane 3. Index a two-slide PPTX fixture. Save `pr2-two-slides.png`. Pass when two rows show slide 1 and slide 2.
- [ ] Lane 4. Query with the Chinese words that appear only on page 2. Save `pr2-chinese-hit.png`. Pass when page 2 is the top row.
- [ ] Lane 5. Query with an English token that appears only on another fixture page. Save `pr2-english-hit.png`. Pass when that page is the top row.
- [ ] Lane 6. Index the same PDF again. Save `pr2-reindex.png`. Pass when the row count is still two.
- [ ] Lane 7. Index a PDF page whose extracted text is empty. Save `pr2-empty-page.png`. Pass when that page has no row.
- [ ] Lane 8. Restart the sandbox and reopen the tool window. Save `pr2-restart.png`. Pass when the same two rows are back without a new index action.
- [ ] Lane 9. Query with tokens that match nothing. Save `pr2-no-hit.png`. Pass when the list says no course chunk matched.
- [ ] Lane 10. Index a PPTX whose master contains a unique token that no slide contains. Save `pr2-no-master.png`. Pass when a query for that token returns no row.

**Verify, perf.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] Metric. Milliseconds inside `CourseIndex.index` for `fixture-two-page.pdf`, and the two rows the user waits for. Trunk has no indexer, so record that trunk cannot run this method.
- [ ] Probe. Call the method on a small harness at head. Run the harness before and after one sandbox start. Record trunk as method absent.
- [ ] Baseline. Record the trunk absence first.
- [ ] Rule. Fail the head when one index call of that fixture takes more than 2000 milliseconds. Do not divide head by trunk. The 2000 millisecond ceiling is an unmeasured budget, not a timing this plan observed.

**Review gate.** The operator reviews before merge.

- [ ] Copy lane 2 screenshots. Save them as pr-2-review-rows.png.
- [ ] Record a 30 to 60 second video of indexing the fixture and running the Chinese query on a lane VM. Save it as pr-2-review.mp4.
- [ ] Post the screenshots and the video in chat. Stop at merge-ready. Wait for the operator's review.

**Merge.**

- [ ] Root's clean verdict at the exact head SHA.
- [ ] Bugbot triage done.
- [ ] Rebased onto PR-1 after the verdict, patch-id unchanged.
- [ ] The owner does not merge. The operator lands PR-2 only after PR-1.

## Answer from course files (PR-3)

**Depends on.** PR-2.

**Files.**

- [ ] Create `src/main/java/cst3492/courseta/ask/Answer.java`.
- [ ] Create `src/main/java/cst3492/courseta/ask/ChatClient.java`.
- [ ] Create `src/main/java/cst3492/courseta/ask/AskAction.java`.
- [ ] Create `src/test/java/cst3492/courseta/ask/AnswerTest.java`.
- [ ] Edit `src/main/resources/META-INF/plugin.xml`.
- [ ] Edit `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`.

**Build.**

- [ ] Add `Answer` with kind `CITED` or `GENERAL`, the answer text, and the `CourseChunk` list that was retrieved.
- [ ] Retrieve on a background thread, paint the chunk path and scope in the tool window, then POST `messages` to the configured chat URL. Read `choices[0].message.content` when the body matches that shape.
- [ ] If the hit list is empty, skip invented citations and show the sentence `Response is based on general knowledge; no specific course material is referenced.`
- [ ] Register `AskAction` in `EditorPopupMenu`. Read the selection from the primary caret offsets. Resolve a method with `PsiFile.findElementAt` and `PsiTreeUtil.getParentOfType` to `PsiMethod`.
- [ ] Return `ActionUpdateThread.BGT` from `getActionUpdateThread`.

**You see.**

- [ ] A question with a hit shows the path and the page or slide number before any answer text arrives.
- [ ] A right-click on a selected method asks about that selection and still shows those citations.

**Verify, unit.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] `AnswerTest` builds a `GENERAL` answer from an empty hit list and asserts the fixed sentence, with no chunk attached. Run `./gradlew test`.

**Verify, live.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked. Ten lanes on `grok-4.7-xhigh-fast` at the PR head, per the boot recipe.

- [ ] Lane 1. Regression lane against trunk. Ask at trunk and at head. If trunk lacks the feature, record that and gate the citation row plus the answer text the user waits for. Save `pr3-trunk-absent.png`. Pass when head shows a citation and trunk has no Ask action.
- [ ] Lane 2. Ask a question that matches one fixture page, with the chat client stubbed to delay two seconds. Save `pr3-citation-first.png`. Pass when the path and page number are visible before the stub returns.
- [ ] Lane 3. Select a method and use the editor right-click Ask action. Save `pr3-right-click.png`. Pass when the question panel contains the selected text and a citation row.
- [ ] Lane 4. Ask a question with no matching chunk. Save `pr3-general.png`. Pass when the panel shows the general-knowledge sentence and no citation row.
- [ ] Lane 5. Remove the API key and ask a matching question. Save `pr3-no-key.png`. Pass when the citation row is visible and the panel names the missing key instead of an answer.
- [ ] Lane 6. Point the base URL at a closed port. Save `pr3-http-down.png`. Pass when the citation row remains and the panel shows the HTTP failure.
- [ ] Lane 7. Read one citation row after a successful stubbed answer. Save `pr3-cite-fields.png`. Pass when the row shows both the course-relative path and the scope number.
- [ ] Lane 8. Ask a second question that matches a different page. Save `pr3-second-question.png`. Pass when the citation list contains only the second page.
- [ ] Lane 9. Right-click with an empty selection. Save `pr3-empty-selection.png`. Pass when the panel says no code is selected and no HTTP call is recorded.
- [ ] Lane 10. Ask a question that is not about code. Save `pr3-non-code.png`. Pass when retrieval still runs and the panel shows either a citation or the general-knowledge sentence.

**Verify, perf.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] Metric. Milliseconds from submit until the citation row is painted, excluding the HTTP round trip, and the answer text the user waits for after the stub returns. Trunk has no Ask action, so record that absence.
- [ ] Probe. Use the stubbed client on head. Start trunk first and record the missing action. Then run the timed submit on head twice.
- [ ] Baseline. Record the trunk absence first.
- [ ] Rule. Fail the head when the citation row appears more than 500 milliseconds after submit on the fixture index. Do not divide head by trunk. The 500 millisecond ceiling is an unmeasured budget for local retrieval, not a timing this plan observed.

**Review gate.** The operator reviews before merge.

- [ ] Copy lane 2 screenshots. Save them as pr-3-review-citation.png.
- [ ] Record a 30 to 60 second video of a right-click ask and a no-hit ask on a lane VM. Save it as pr-3-review.mp4.
- [ ] Post the screenshots and the video in chat. Stop at merge-ready. Wait for the operator's review.

**Merge.**

- [ ] Root's clean verdict at the exact head SHA.
- [ ] Bugbot triage done.
- [ ] Rebased onto PR-2 after the verdict, patch-id unchanged.
- [ ] The owner does not merge. The operator lands PR-3 only after PR-2.

## Generate unit tests from a scenario (PR-4)

**Depends on.** PR-3.

**Files.**

- [ ] Create `src/main/java/cst3492/courseta/junitgen/GenerateTestAction.java`.
- [ ] Create `src/test/java/cst3492/courseta/junitgen/GenerateTestActionTest.java`.
- [ ] Edit `src/main/resources/META-INF/plugin.xml`.
- [ ] Edit `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`.

**Build.**

- [ ] Register `GenerateTestAction` in `EditorPopupMenu` for a selected `PsiClass` or `PsiMethod`.
- [ ] Open a text field first. Do not call `ChatClient` until the field has non-empty text.
- [ ] Send the scenario text and the selected source to `ChatClient`. Show the returned JUnit source in the tool window.
- [ ] Add an Insert button that writes the preview into a new test file through a write command. Do not write the file before that click.

**You see.**

- [ ] The first panel after the action is a scenario field, with no test source yet.
- [ ] After a stubbed reply, the preview shows a class that names JUnit, and the editor is unchanged until Insert.

**Verify, unit.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] `GenerateTestActionTest` asserts an empty scenario does not call the chat client. Run `./gradlew test`.

**Verify, live.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked. Ten lanes on `grok-4.7-xhigh-fast` at the PR head, per the boot recipe.

- [ ] Lane 1. Regression lane against trunk. Run the action at trunk and at head. If trunk lacks the feature, record that and gate the scenario field plus the preview the user waits for. Save `pr4-trunk-absent.png`. Pass when head shows the field and trunk has no such action.
- [ ] Lane 2. Select a method and invoke the action. Save `pr4-scenario-first.png`. Pass when the scenario field is focused and no test source is visible.
- [ ] Lane 3. Submit an empty scenario. Save `pr4-empty-scenario.png`. Pass when no chat request is recorded.
- [ ] Lane 4. Submit a scenario and return a stubbed JUnit class. Save `pr4-preview.png`. Pass when the preview contains the stubbed class and the editor file is unchanged.
- [ ] Lane 5. Click Insert. Save `pr4-inserted.png`. Pass when a new test file is open and its text matches the preview.
- [ ] Lane 6. Invoke the action and close the field without Insert. Save `pr4-cancel.png`. Pass when no new file exists.
- [ ] Lane 7. Select a class rather than a method, then generate. Save `pr4-class-selection.png`. Pass when the preview appears and the request contains the class source.
- [ ] Lane 8. Invoke the action with no selection. Save `pr4-no-selection.png`. Pass when the panel says to select a class or a method and no chat request is recorded.
- [ ] Lane 9. Read the preview for the import line. Save `pr4-junit-import.png`. Pass when the preview contains `org.junit`.
- [ ] Lane 10. Insert once, invoke again, and cancel. Save `pr4-second-cancel.png`. Pass when the first inserted file is unchanged.

**Verify, perf.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] Metric. Milliseconds from a non-empty scenario submit until the preview is painted, with `ChatClient` stubbed to return at once, and the preview the user waits for. Trunk has no action, so record that absence.
- [ ] Probe. Run the stubbed submit on head twice. Record trunk as the action absent before the first head run.
- [ ] Baseline. Record the trunk absence first.
- [ ] Rule. Fail the head when the preview appears more than 500 milliseconds after the stub returns. Do not divide head by trunk. The 500 millisecond ceiling is an unmeasured budget, not a timing this plan observed.

**Review gate.** The operator reviews before merge.

- [ ] Copy lane 4 screenshots. Save them as pr-4-review-preview.png.
- [ ] Record a 30 to 60 second video of a scenario, a preview, and Insert on a lane VM. Save it as pr-4-review.mp4.
- [ ] Post the screenshots and the video in chat. Stop at merge-ready. Wait for the operator's review.

**Merge.**

- [ ] Root's clean verdict at the exact head SHA.
- [ ] Bugbot triage done.
- [ ] Rebased onto PR-3 after the verdict, patch-id unchanged.
- [ ] The owner does not merge. The operator lands PR-4 only after PR-3.

## Draft commit messages (PR-5)

**Depends on.** PR-4.

**Files.**

- [ ] Create `src/main/java/cst3492/courseta/commit/CommitMessageAction.java`.
- [ ] Create `src/test/java/cst3492/courseta/commit/CommitMessageActionTest.java`.
- [ ] Edit `src/main/resources/META-INF/plugin.xml`.
- [ ] Edit `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`.

**Build.**

- [ ] Depend on plugin id `Git4Idea`.
- [ ] Return `ActionUpdateThread.BGT` from `getActionUpdateThread` on `CommitMessageAction`. Do not run `git diff` inside `update`.
- [ ] On a background thread, treat an empty `GitRepositoryManager.getRepositories()` as no repository. Treat an empty `GitChangeUtils.getStagedChanges` on every root as nothing staged. In both cases, show that fact and do not call `ChatClient`.
- [ ] When a root has staged changes, run `GitLineHandler` for `GitCommand.DIFF` with `--cached` and `-M`. Concatenate stdout. Do not call `GitCommand.COMMIT`. Paint the three candidates on the EDT.
- [ ] Ask the model for three conventional-commit candidates. Show all three. Copy puts one candidate on the clipboard. A button in the `Folio` window runs the same action as the registration in `Vcs.MessageActionGroup`.

**You see.**

- [ ] A repository with one staged file shows three message candidates and the staged diff text.
- [ ] After Copy, `git status` still shows that file staged.

**Verify, unit.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] `CommitMessageActionTest` feeds an empty staged list and asserts the chat client is not called. Run `./gradlew test`.

**Verify, live.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked. Ten lanes on `grok-4.7-xhigh-fast` at the PR head, per the boot recipe.

- [ ] Lane 1. Regression lane against trunk. Run the action at trunk and at head. If trunk lacks the feature, record that and gate the three candidates plus the still-staged file the user waits for. Save `pr5-trunk-absent.png`. Pass when head shows three candidates and trunk has no such action.
- [ ] Lane 2. Stage one line in a sandbox repo and run the action from the tool window. Save `pr5-three-messages.png`. Pass when three non-empty candidates are visible.
- [ ] Lane 3. Run the action with a clean index. Save `pr5-nothing-staged.png`. Pass when the panel says nothing is staged and no chat request is recorded.
- [ ] Lane 4. Open a project that is not a Git repository. Save `pr5-no-repo.png`. Pass when the panel says the project has no Git repository and no chat request is recorded.
- [ ] Lane 5. Change a file and do not stage it. Save `pr5-unstaged.png`. Pass when the panel says nothing is staged.
- [ ] Lane 6. Copy the first candidate. Save `pr5-copied.png`. Pass when the clipboard equals that candidate.
- [ ] Lane 7. Read `git status` after the action. Save `pr5-still-staged.png`. Pass when the staged file is still staged and no new commit exists.
- [ ] Lane 8. Run the same action from the commit-message toolbar. Save `pr5-toolbar.png`. Pass when the same three candidates appear.
- [ ] Lane 9. Force the diff command to fail. Save `pr5-diff-failed.png`. Pass when the panel shows the command failure and no candidate.
- [ ] Lane 10. Return a stub that ignores the conventional-commit shape. Save `pr5-free-form.png`. Pass when the panel still shows the returned text.

**Verify, perf.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] Metric. Milliseconds to collect `git diff --cached` text for one staged line, and the three candidates the user waits for after the stub returns. Trunk has no action, so record that absence.
- [ ] Probe. Time the diff collection on head twice. Record trunk as the action absent before the first head run.
- [ ] Baseline. Record the trunk absence first.
- [ ] Rule. Fail the head when collecting that one-line diff takes more than 2000 milliseconds. Do not divide head by trunk. The 2000 millisecond ceiling is an unmeasured budget, not a timing this plan observed.

**Review gate.** The operator reviews before merge.

- [ ] Copy lane 2 screenshots. Save them as pr-5-review-messages.png.
- [ ] Record a 30 to 60 second video of a staged edit, three candidates, and a still-staged file on a lane VM. Save it as pr-5-review.mp4.
- [ ] Post the screenshots and the video in chat. Stop at merge-ready. Wait for the operator's review.

**Merge.**

- [ ] Root's clean verdict at the exact head SHA.
- [ ] Bugbot triage done.
- [ ] Rebased onto PR-4 after the verdict, patch-id unchanged.
- [ ] The owner does not merge. The operator lands PR-5 only after PR-4.

## Add the course drill (PR-6)

**Depends on.** PR-5.

**Files.**

- [ ] Create `src/main/java/cst3492/courseta/drill/CourseDrillAction.java`.
- [ ] Create `src/test/java/cst3492/courseta/drill/CourseDrillActionTest.java`.
- [ ] Edit `src/main/resources/META-INF/plugin.xml`.
- [ ] Edit `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`.

**Build.**

- [ ] Register `CourseDrillAction` in `EditorPopupMenu`. Require a non-empty selection.
- [ ] Retrieve chunks for the selection, then ask `ChatClient` for three short questions about that selection. Show each question with the chunk path and scope. Do not write into the editor.
- [ ] If the hit list is empty, show the same general-knowledge sentence and do not invent a slide number.
- [ ] Keep this action until the operator replaces it. Appendix A records the other two candidates.

**You see.**

- [ ] A selected method yields three questions. Each question shows a path and a scope number when a chunk matched.
- [ ] The editor text is the same after the action as before it.

**Verify, unit.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] `CourseDrillActionTest` asserts an empty selection does not call the chat client. Run `./gradlew test`.

**Verify, live.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked. Ten lanes on `grok-4.7-xhigh-fast` at the PR head, per the boot recipe.

- [ ] Lane 1. Regression lane against trunk. Run the drill at trunk and at head. If trunk lacks the feature, record that and gate the three questions plus the unchanged editor the user waits for. Save `pr6-trunk-absent.png`. Pass when head shows three questions and trunk has no drill action.
- [ ] Lane 2. Select a method that matches a fixture page and run the drill with a stub. Save `pr6-three-questions.png`. Pass when three questions are visible.
- [ ] Lane 3. Read the citation under the first question. Save `pr6-question-cite.png`. Pass when that citation shows a path and a scope number.
- [ ] Lane 4. Run the drill with no selection. Save `pr6-no-selection.png`. Pass when the panel names the missing selection and no chat request is recorded.
- [ ] Lane 5. Run the drill before any index exists. Save `pr6-no-index.png`. Pass when the panel shows the general-knowledge sentence.
- [ ] Lane 6. Compare the editor buffer before and after the drill. Save `pr6-editor-unchanged.png`. Pass when the buffer text is unchanged.
- [ ] Lane 7. Select a different method and run the drill again. Save `pr6-second-selection.png`. Pass when the request text contains the second selection and not only the first.
- [ ] Lane 8. Force an empty hit list. Save `pr6-general.png`. Pass when no scope number is shown.
- [ ] Lane 9. Confirm the panel does not also render a long answer under the questions. Save `pr6-questions-only.png`. Pass when the panel has three questions and no second answer block.
- [ ] Lane 10. Restart the sandbox and run the drill on the same selection. Save `pr6-after-restart.png`. Pass when three questions appear again.

**Verify, perf.** Tests alone are not sufficient verification. A PR is verified only when its unit, live, and perf boxes are all checked.

- [ ] Metric. Milliseconds from the drill invoke until the three question slots are reserved on screen, before the stub returns, and the three questions the user waits for. Trunk has no drill, so record that absence.
- [ ] Probe. Time that paint on head twice with the stub delayed. Record trunk as the action absent before the first head run.
- [ ] Baseline. Record the trunk absence first.
- [ ] Rule. Fail the head when the three slots are not reserved within 500 milliseconds of the invoke, before the stub returns. Do not divide head by trunk. The 500 millisecond ceiling is an unmeasured budget, not a timing this plan observed.

**Review gate.** The operator reviews before merge.

- [ ] Copy lane 2 screenshots. Save them as pr-6-review-questions.png.
- [ ] Record a 30 to 60 second video of a selection, three questions, and an unchanged editor on a lane VM. Save it as pr-6-review.mp4.
- [ ] Post the screenshots and the video in chat. Stop at merge-ready. Wait for the operator's review.

**Merge.**

- [ ] Root's clean verdict at the exact head SHA.
- [ ] Bugbot triage done.
- [ ] Rebased onto PR-5 after the verdict, patch-id unchanged.
- [ ] The owner does not merge. The operator lands PR-6 only after PR-5.

## Close the program

- [ ] Every box above is checked with its evidence.
- [ ] Reply to the operator with each PR id, its verdict, and the head SHA. Do not report a merge.

## Appendix A. Prototype evidence

The tokenizer fork was observed. The scratch repo is `/tmp/rag-token-prototype` on branch `master` at SHA `02ce44c11aac51522a57c4bc63a9d65a19149ccd`. The log is `observed.txt` in that directory. There is no screenshot because the check printed ranks.

A query `一对多怎么映射` scored 0 against every fixture under whitespace tokens. The same query scored 2 against `lecture-jpa.pdf` page 2 under ASCII words plus character bigrams, and 0 against the other two fixtures. A query `doGet` hit `lecture-servlet.pdf` page 1 under both tokenizers. PR-2 therefore indexes both kinds of tokens. The fixture sentences were written for this check. They are not the course slides. Whether bigrams rank a real deck is unproven.

No UI prototype was built. The brief already names a tool window or a sidebar, and it names the editor right-click. PR-1 uses the tool window. PR-3 uses the right-click.

These calls stay with the operator. The plan does not pretend a run answered them.

The lab or personal IDE version for the presentation is unproven. The plan pins `2026.2.4` because the JetBrains release feed lists that version as build `262.11879.100` on 30 October 2026. An older lab image will refuse the plugin.

The chat account is unproven. DeepSeek `POST https://api.deepseek.com/chat/completions` is the verified body, with required fields `model` and `messages`, and documented model ids `deepseek-flash` and `deepseek-v4-pro`. The Tongji host `https://agi.tongji.edu.cn/` returned a login page and no JSON body. PR-3 keeps the base URL, the model name, and the key in settings. The preset text in the README example is the DeepSeek URL until the operator names another account.

The third smart action is unproven. PR-6 builds the course drill unless the operator replaces it before that PR starts. The other shapes are in Appendix B.

The real lecture files are unproven. Tests use fixtures. The Index files button is how the team loads the real deck before the demo.

## Appendix B. Alternatives rejected

Vector retrieval lost. The brief requires chunks, an index, a most-relevant lookup, and a citation. It does not require embeddings. LangChain4j lists full-text search as one RAG method. A network embedding call would make retrieval as slow as the API, which hides the citation when the demo network fails. The local ONNX example uses `bge-small-en-v1.5`, an English model, and the slides may be Chinese. That failure mode is a guess from the model name. It was not measured on a deck.

Shipping Lucene inside the plugin lost. IntelliJ Community master bundles `lucene-core` 10.5.1, and that bundle is not a documented plugin API. A second Lucene jar is a classpath fight for a two-day demo. The token map is enough for the citation demo.

LangChain4j as the chat client lost. A JDK HTTP POST matches the verified DeepSeek body with one less library whose sample URL disagrees with the current DeepSeek page. Add `OpenAiChatModel` only if a later review demands that name on the slide.

An editor action for the commit message lost. The input is the project index, not the open file. The action is registered on `Vcs.MessageActionGroup` and also as a button in the tool window so the demo does not depend on finding the commit toolbar.

Writing the JUnit file immediately lost. The brief does not require an automatic write. A preview plus Insert lets the speaker show the scenario before the file changes.

Automatic `git commit` lost. The brief says to display the message.

Three creative shapes were weighed. The course drill asks three questions about the selection and cites a chunk. A lab-gap list would check the selection against rules in the slides, and it fails when the slides do not state those rules. A console-exception explainer answers a question, which repeats F0. The drill is the default because the panel shows questions rather than an answer.

F2 lost for this group. The brief assigns code modification and the built-in diff viewer to group C.

## Appendix C. Risks

The IDE window has no `control-ui` or `control-cli` skill. Every live lane still names `./gradlew test` and `./gradlew runIde`. A lane that cannot attach to the sandbox window fails closed and does not count a green test report as the screenshot.

`GitChangeUtils.getStagedChanges` and `GitLineHandler` are in the unreviewed Git API dump on `intellij-community` master. The owner checks those signatures against the `2026.2.4` SDK at the start of PR-5. A missing method means the diff text comes from `git diff --cached` through `GitLineHandler` only, and the empty-index check uses that command's empty stdout.

`LightJavaCodeInsightFixtureTestCase5` was named by the platform notes and its package was unverified. The owner confirms the class in the `2026.2.4` SDK before PR-1 tests compile.

PPTX scope numbers in Tika `4.2.0` follow slide relationship order. The plan calls that number a slide scope. It does not claim the number painted on the slide until someone checks a real deck.

The Gradle wrapper patch for November 2026 was unverified. PR-1 reads it from the Gradle release page.

`untilBuild` `262.*` will not load on IDEA `2026.3` or on `2025.3`. That is the lab-version risk in Appendix A.

The English report PDF, the English slide PDF, and the 20 minute presentation are not code PRs. The team writes them after PR-6. The report order is member names and numbers, the feature list, the user manual, the architecture section with the chunk and index description, then any extra notes. The ZIP keeps source in one folder and the two PDFs in another folder. Canvas receives one ZIP before 11.00pm on 3 November 2026. The presentation window is 1.30-4.15pm on 4 November 2026. The brief does not name a time zone.

The program checklist reads `pstack` paths from `origin/main`. This repository does not contain those paths. On the operator's go, the owner reads the same files from the installed poteto-mode plugin and records that fallback. A missing `control-ui` read is expected.

## Appendix D. Links and reading list

Read these before editing.

- Assignment extraction used for F0 and F1, kept beside the chat that wrote this plan.
- [IntelliJ plugin notes](research/intellij-plugin.md)
- [Retrieval library notes](research/rag-libraries.md)
- [Staged diff notes](research/git-staged-diff.md)
- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Gradle Plugin Portal page for org.jetbrains.intellij.platform](https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform)
- [JetBrains release feed for IIU](https://data.services.jetbrains.com/products/releases?code=IIU&type=release&latest=true)
- [DeepSeek chat completions](https://api-docs.deepseek.com/api/create-chat-completion)
- [Apache Tika download](https://tika.apache.org/download)

PR-2 and PR-3 get a how pass over the index and the ask flow before their owners edit. PR-5 gets a how pass over the staged diff. PR-6 gets an interrogate pass because the third action is the team's own design. The decision trail for the run lives with the execution notes the operator asks for. This plan file is the trail for the choices above.

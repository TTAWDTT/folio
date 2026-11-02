# Folio

Folio is an IntelliJ IDEA plugin for CST3492 Group B. It answers questions from indexed course files, uses selected code as context, generates JUnit previews, drafts commit messages, and creates short course drills.

Group B requires F0 course retrieval and F1 IDE actions. Source-code editing and the built-in diff viewer belong to Group C. Folio retains an explicit **Write test file** action for a validated test preview. It does not execute Git commits or change staging.

The plugin id is `cst3492.folio`, version `0.1.0`. The **Folio** tool window is on the right. [review.md](review.md) records the review, selected designs, implementation status, and verification gaps. The original course plans are [English](docs/course-ta-plugin-plan.md) and [Chinese](docs/course-ta-plugin-plan.zh.md).

## Use Folio in a course project

1. Open **Folio**. On **Files**, choose **Index files** and select course materials. Indexing needs no API key. Materials are shared across projects in the same IDE configuration.
2. On **Ask**, enter a course question. Without a key, Folio retrieves local sources and explains that model configuration is missing. It makes no HTTP request.
3. Open the gear to configure the endpoint, model, and API key. A typed key can be used for the next request before saving. **Save settings** stores it in Password Safe and reports success after the write finishes. An empty key field keeps the stored key. **Clear API key** removes it.
4. Send a question with a configured key. Folio shows the answer and labels whether its cards are inline citations, retrieved context, or a citation fallback. Follow-up questions use bounded conversation context. **Stop** cancels active work. **Clear chat** clears the conversation and prevents a late reply from restoring it.
5. Select a Java class or method and run **Generate JUnit From Scenario** in the editor menu. Accept a natural-language scenario to generate a preview. Inspect the Java, target path, and validation note before choosing **Write test file**.
6. Stage changes and run **Draft Folio Commit Messages** from the commit dialog or the Commit tab. Folio captures every configured Git root, shows the captured diff and call estimate, and asks for three candidates. **Copy** and **Fill commit box** remain available for the current, unchanged staged snapshot.

The composer supports Enter to send, Shift+Enter for a newline, and IME composition. It grows to six rows and then scrolls. The header wraps at narrow widths, and section buttons retain their tooltips when labels collapse. The UI follows the IDE theme and zoom.

## Ask and citation behavior

Retrieval is local BM25, without embeddings or a web search. Each request captures a read-only course-library snapshot, the current question, selected code, conversation context, and connection settings. Retrieval uses the current question and selection and returns at most four chunks. History is quoted context, not current citation evidence.

A source card shows the document, page, slide, or section, any chunk part, and an excerpt. Clicking a text file on disk opens the editor near the excerpt. PDFs, slide decks, other non-text files, and missing paths use an indexed-chunk preview.

The evidence label comes from `Answer.Evidence`:

| Evidence | Meaning |
| --- | --- |
| `GENERAL` | No course chunk supports the current retrieval context. The answer is presented as general knowledge. |
| `INLINE` | The reply contains mapped prose references to retrieved chunks. |
| `RETRIEVED` | Course chunks were supplied, but the reply has no mapped prose references. Cards show retrieved context. |
| `INVALID` | The mapper removed out-of-range prose references. Remaining cards do not make those references valid. |
| `AMBIGUOUS` | A literal number conflicts with citation numbering. The mapper preserves in-range numbers and lists all retrieved chunks. |
| `CONFIGURATION` | No model call was made because a key is missing. Local retrieval cards remain available. |

These labels describe provenance and citation syntax. They do not verify whether a model's claims follow from the material. With no relevant chunks, Folio states that the answer is not grounded in course files and removes invented prose references.

The visible chat holds at most 40 exchanges. Conversation context retains at most 40 completed Ask turns, including the selected code captured for each turn. The next request captures up to eight complete turns within 8,000 characters. `Asker` preserves every accepted history character as quoted data, including Java indexes and earlier reference numbers. History above 8,000 characters is rejected explicitly. Earlier references are not current citation evidence. Failed requests, configuration-only responses, and drills do not become Ask history.

**Ask Folio** in the editor asks about the selection. **Folio Course Drill** requests three short questions about selected code through the same retrieval and citation path. An empty drill selection makes no model call.

<details>
<summary>Citation compatibility reference</summary>

`Asker.mapCitations` retains the existing handling of prose, code, and mixed numbering:

- Valid prose markers are renumbered in first-citation order. Repeated original numbers retain the same new number. The source list follows those numbers.
- Adjacent `[1][2]`, lists such as `(see [1], [2])`, line-start `*`, `-`, or `>` markers, closing emphasis such as `**A** [1]`, and prose parentheses such as `Servlets ([1])` are mapped. Chinese prose and full-width parentheses follow the same rule.
- Unknown prose numbers are removed. Citation-only connectors such as `and`, `cf.`, or `和` and empty citation parentheses are removed with them. Ordinary words and sentence punctuation remain. For example, `Servlets [9] and JPA` becomes `Servlets and JPA`, and `(HttpServlet [9])` becomes `(HttpServlet)`.
- Calls, indexes, and expression lists stay literal. Examples include `foo([1])`, `foo()([1])`, `m[0]([1])`, `m[0][2]`, `print([1, 2, 3])`, `return [1, 2]`, and `x = [1, 2]`. ASCII names including `$` count as call names. A full-width `（` remains prose.
- A readable expression such as `a + ([1])` or a Markdown link label such as `[2](url)` can conflict with a retrieved number. Folio then preserves in-range numbering, returns every retrieved chunk in retrieval order, and reports `AMBIGUOUS`. Out-of-range prose references are still removed.
- Backtick and tilde fences, fences indented by up to three spaces, four-space code blocks, and inline code remain unchanged. A citation after a closing fence is still mapped. Two trailing spaces that form a Markdown line break are preserved.
- A reply without citation markers keeps all retrieved chunks as context. A reply whose prose markers are all invalid has no source cards and reports invalid source markers.

`AskerTest` covers these compatibility cases, including seeded mixed prose/code examples. Citation mapping is not a semantic answer-quality test.

</details>

## Course files and recovery

**Index files** accepts multiple files. PDF page extraction preserves original page numbers, including blank-page gaps. PPTX extraction preserves slide order and includes nested groups, table cells, and attached notes. Slide-master content is excluded. TXT, Markdown, and DOCX fixtures use Tika's non-paginated body fallback and are labeled **section**, without invented page numbers. Other Tika-readable formats can be selected, but do not have the same format-specific regression coverage.

`CourseChunks` splits each page, slide, or section into at most 700 Java characters with a 100-character overlap. It prefers a space or newline in the latter half of a chunk and avoids splitting UTF-16 surrogate pairs. Split chunks retain their original scope number and a part identifier.

Sources use absolute paths, so two `notes.pdf` files in different directories remain distinct. Re-indexing replaces that source. **Remove** asks for confirmation. Re-index and Remove also clear a matching legacy bare-name row without removing another absolute path with the same basename.

`CourseLibrary` parses and validates the whole selected batch before committing it. A missing file, parser failure, zero-text result, or cancellation before commit preserves the old library. Overlapping changes to the same source are rejected with a retry message. Independent-source changes merge under the store lock. A cancellation after the atomic commit does not undo that committed revision.

The library lives under the IDE configuration directory:

| File | Purpose |
| --- | --- |
| `folio/index.json` | Schema-versioned chunks, library revision, and source revisions. |
| `folio/index.backup.json` | One previous valid JSON version. |
| `folio/index.lock` | Process coordination during reads and commits. |
| `folio/index.tsv` | Legacy import input. Migration preserves the original. |

The store validates a temporary JSON file before an atomic replacement. A damaged current file falls back to a valid backup with a warning. If both are invalid, loading fails explicitly. Legacy migration also rejects malformed rows. Unsupported atomic moves fail rather than report a successful save. The protocol does not guarantee directory durability during power loss.

All open Folio panels in the same IDE process observe committed library snapshots. Separate IDE processes coordinate writes and reread current state before committing, but do not receive cross-process push updates.

### Retrieval rules

`CourseIndex.top` uses BM25 with `k1 = 1.2` and `b = 0.75`. A chunk must cover at least half the question's coverage units. Among qualifying chunks, scores below 25% of the best score are dropped.

English terms are lowercase words with a stop list and light stemming. Coverage ignores courtesy words and general task verbs when more specific terms remain. Those verbs stay indexed and remain searchable on their own. Chinese, Japanese, and Korean terms use overlapping bigrams. Chinese coverage groups runs of content characters and removes listed courtesy phrases. `Tokens` contains the exact word and character lists.

The fixed public fixture has 12 positive and 12 negative queries over synthetic Java lecture text. The before/after check retained 12 of 12 positive hits and reduced false hits from 2 of 12 to 0 of 12. A separate local extraction check retained 3 real course PDFs, 85 page scopes, and 90 chunks. These are bounded extraction and retrieval checks, not model-answer quality measurements.

## JUnit preview and explicit write

**Generate JUnit From Scenario** opens a multiline scenario dialog. Cancel or an empty accept makes no generation request. Generation requires captured Java source and a key. The request keeps its scenario, source, target directory, and connection together.

The Test tab separates the original model reply from the Java preview. `GeneratedTest.parse(Project, raw)` extracts one Java unit from a plain reply or one fenced block. IntelliJ PSI checks Java syntax and recognizes common JUnit annotations, nested containers, and basic method signatures. The filename comes from the parsed top-level declaration. The preview states, "Java syntax checked. Recognized JUnit annotations found. Not compiled or run."

A valid preview with no captured directory remains visible but cannot be written. Folio prefers an existing matching package directory under the same module's Java test-source root. When none exists, it retains the adjacent source directory as the fallback. The target path is shown before writing.

**Write test file** uses the current accepted preview and its original directory, not the transcript or a later selection. It rejects a closed project, unavailable or read-only directory, mismatched package, and existing filename. It creates the file through an IntelliJ write command, opens the created file, and supports IDE Undo. The model is not called again. A newer preview, Stop, or disposal prevents an old request from becoming the writable result.

These checks do not establish runner-valid JUnit. They do not validate factory return compatibility, argument providers, generic types, inherited or composed tests, dependencies, discovery, execution, or test quality. Use the project's test command after inspection.

## Commit candidates from all staged changes

Folio captures each configured Git root and falls back to the project base when no roots are configured. Capture includes the staged inventory, full text patch, binary metadata, renames, deletions, submodule changes, and initial commits. It disables external diff and textconv execution. The captured fingerprint binds candidates to that staged state.

A single request handles a small diff. Larger diffs use ordered batches of at most 12,000 Java characters, followed by one synthesis request over every returned batch summary. No tail is silently discarded. Missing summaries and size or call limits fail explicitly without presenting a complete draft. Processing every raw character does not prove that the model's summaries retain every semantic detail.

The Commit tab shows roots, staged file count, character and call estimates, candidates, and the captured diff. It asks the model for three conventional commit messages separated by `---`. The number returned still depends on the provider response.

Automatic copying or filling requires a current result, unchanged staging, and an unchanged original commit box. Automatic copying also preserves clipboard contents changed during generation. Explicit **Copy** and **Fill commit box** recheck staging and task identity. A full fingerprint check runs in the background. Delivery checks captured index and reference-file metadata immediately before a clipboard or commit-box write, including linked worktrees, split indexes, and reftable references. This catches ordinary queued changes. The file observation and UI write do not form an atomic operation against arbitrary external writes. Fill preserves user edits and refuses a closed or unreadable target. These actions do not execute a commit or alter staging.

## Connection settings and limits

| Setting | Storage | Default |
| --- | --- | --- |
| Base URL | Application `folio.xml`, through `TaSettings` | `https://api.deepseek.com` |
| Model | Application `folio.xml` | `deepseek-flash` |
| Credential origin | Application `folio.xml`, scheme, host, and port only | none |
| API key | IntelliJ Password Safe, service generated from `Folio` and `apiKey` | none |

`ProviderAccess` orders credential loading, saving, and clearing on one background executor. Its badge distinguishes loading, saving, clearing, ready, and failed states. A save reports success after persistence. Newer edits are not cleared by an older callback.

A non-empty typed key takes precedence for a request. An empty field uses a stored key only for its saved HTTP or HTTPS origin. Changing origin requires a typed or newly saved key. While credentials are loading, saving, or clearing, Folio asks you to wait or enter a key for the request. It does not use the old cached key. An empty Save retains the stored key and its origin binding.

The endpoint accepts HTTP and HTTPS, including remote HTTP hosts and query parameters. Folio appends `/chat/completions` unless that path is already present and preserves the query. URL credentials, fragments, unsupported schemes, invalid hosts, and invalid ports are rejected. Requests send a bearer token. Errors redact the actual submitted key, and connection objects do not print secrets.

The following limits use Java characters unless the unit says bytes. They are not token budgets.

| Boundary | Limit |
| --- | --- |
| Current Ask question plus selected code | 48,000 characters, with explicit refusal above the limit. |
| Ask system and user prompt | 64,000 characters. |
| HTTP model, system, and user text | 100,000 characters. |
| HTTP response | 1,048,576 bytes. |
| HTTP error detail | 500 characters before status and truncation text. |
| HTTP timeouts | 20-second connection timeout and 60-second request timeout. |
| Generated-test reply | 100,000 characters. |
| Raw staged diff for generation | 240,000 characters. |
| Commit batches and calls | At most 20 batches and 21 total calls. Continuation headers count toward each 12,000-character batch. |
| Commit summary or final response | 8,000 characters each. |
| Commit synthesis input | 24,000 characters. |
| Commit cumulative system and user input | 280,000 characters. |
| Each Git process output | 4,194,304 bytes and a 30-second deadline. |

Continuation overhead can make a near-limit diff exceed the batch count. Folio refuses that request rather than omit content.

## Build and verification

The build requires JDK 25 and resolves IntelliJ IDEA 2026.2.4. The plugin targets platform builds `262` through `262.*` and depends on the Java plugin. A compatible chat endpoint is needed only for model-backed actions.

Run these commands from the repository root with JDK 25 in `JAVA_HOME`:

```sh
./scripts/verify.sh
```

A nonzero exit means verification failed. The script runs `cleanTest test buildPlugin`, then rejects missing or empty required test suites, failures, errors, skipped tests, a corrupt ZIP, or missing plugin classes. It writes the measured count and plugin checksum to `build/verification/summary.json`. That generated summary is the result for the current checkout. A previous count is not proof of a new run.

```sh
./gradlew runIde
```

A nonzero exit means the sandbox launch failed. Successful launch alone does not verify a workflow. Open **Folio** in the sandbox. The `Folio tool window created` log confirms that its factory ran. `runIde` can require a first-time Compose hot-reload-agent download.

GitHub Actions uses `scripts/verify.sh` and retains the summary, JUnit XML, and plugin ZIP as artifacts. This repository has not established a hosted CI pass merely by adding the workflow.

### Maven Central behind a firewall

An external Gradle init script can add your Maven mirror. Replace the placeholder path with an existing script, then run:

```sh
./gradlew test buildPlugin -I /path/to/your-mirror.init.gradle
```

A nonzero exit means tests or packaging failed. The mirror script does not retarget JetBrains IDE repositories. Keep machine-specific mirror configuration outside the repository.

## Architecture and ownership

`CourseLibrary` and `ProviderAccess` are application services. The first owns shared materials and committed revisions. The second owns connection settings and ordered Password Safe access. Each `FolioPanel` owns its task manager, bounded conversation, current results, and Swing presentation for one project/content lifetime.

`FolioToolWindowFactory` creates the panel, registers it in project user data through `FolioPanels`, and binds disposal to the tool-window content. Editor and VCS actions activate the tool window and find that project's panel. `FolioChrome` supplies the layout, cards, composer, responsive sections, and colors.

The EDT captures immutable inputs. Background tasks use those values and read-only `CourseSearch` snapshots. `FolioTasks` tracks request IDs and typed success, failure, and cancellation outcomes. Test and Commit accept only the latest request. Delivery and later Write, Copy, or Fill require a live owner and current accepted result. Disposal cancels tasks and closes subscriptions. The activity indicator derives from active tasks.

| Package | Main responsibility |
| --- | --- |
| `cst3492.courseta` | Tool window, project task and conversation state, application provider settings, Password Safe adapter, guarded commit target, and source preview. |
| `cst3492.courseta.ask` | `Asker`, typed `Answer`, citation mapping, injectable `ChatClient`, and bounded `HttpChatClient`. |
| `cst3492.courseta.index` | `CourseLibrary`, read-only `CourseSearch`, `CourseIndex`, parsing, chunking, tokens, and page/slide/section scopes. |
| `cst3492.courseta.junitgen` | Editor scenario action, `TestDraft`, PSI-backed `GeneratedTest`, and explicit IDE write. |
| `cst3492.courseta.commit` | Git snapshot capture, full-diff batching, candidates, and commit-control adapter. |
| `cst3492.courseta.drill` | Selected-code questions through `Asker`. |

![Folio components](docs/images/components.svg)

### Historical statechart

The files under `docs/statechart/` model the earlier serial tool-window implementation. The chart is retained as a historical baseline. It does not model the current task channels, cancellation, request identities, application services, or immutable result targets. Its TLC result is not proof of current concurrency or write safety.

![Historical Folio tool-window statechart](docs/statechart/chart.svg)

The historical sources are [FolioWindow.tla](docs/statechart/FolioWindow.tla), [FolioWindow.cfg](docs/statechart/FolioWindow.cfg), [view.mjs](docs/statechart/view.mjs), and [chart.mmd](docs/statechart/chart.mmd).

## Verification scope and remaining checks

The pre-change baseline ran 138 tests and built the plugin. Five supplied acceptance assertions failed against the original implementation before the repairs. The integrated core later ran 202 tests and built the plugin before frontend integration. The frontend separately ran 49 scoped tests and built the plugin. The original 18 real-Project PSI cases checked generated bytes, collisions, packages, opening, and Undo. Six later cases check actual nested-test containers, reject ordinary helpers and invalid basic method signatures, and retain dynamic factories. The structural checks recognize direct tests, Jupiter `@Nested` containers, and JUnit 4 `Enclosed` containers. They do not establish dependency resolution or successful discovery by a real test runner. These scoped results overlap and must not be added into a final suite count. The final integrated `scripts/verify.sh` run completed with 244 tests across 24 suites, zero failures, errors, or skips, and a validated plugin ZIP. Run the same command for a new checkout.

The deterministic tests cover shared-store commits and recovery, extraction fixtures, citation compatibility, staged-diff coverage, loopback HTTP boundaries, request identity, ordered settings, history, and existing keyboard/layout behavior. Real course-file checks preserved the 3-file, 85-page, 90-chunk denominator.

No live model call was completed during this review. The school API requires the campus network, as confirmed by the user. Its address timed out on the current network. Computer use could not attach to the exact running IDEA process, so a full live walkthrough remains open for Password Safe, multiple projects, source navigation, test-root selection, and actual commit controls. PSI and headless Swing tests do not close those integration gaps. The gaps describe missing verification, not established product defects.

## Historical screenshot gallery

These screenshots came from an earlier IntelliJ IDEA 2026.2.4 Islands session. They preserve the earlier layout and theme reference. They do not show the current Stop/Clear controls, evidence labels, settings lifecycle, or validated Write behavior, and are not end-to-end verification.

Ask in Islands Light:

![Historical Folio Ask, Islands Light](docs/images/ask-islands-light.png)

Files in Islands Light:

![Historical Folio Files, Islands Light](docs/images/files-islands-light.png)

Test in Islands Light. The enabled Write button predates validated previews:

![Historical Folio Test, Islands Light](docs/images/test-islands-light.png)

Settings in Islands Light:

![Historical Folio settings, Islands Light](docs/images/settings-islands-light.png)

Ask in Islands Dark:

![Historical Folio Ask, Islands Dark](docs/images/ask-islands-dark.png)

Settings in Islands Dark:

![Historical Folio settings, Islands Dark](docs/images/settings-islands-dark.png)

## Project layout

```text
build.gradle.kts          Java 25, IntelliJ 2026.2.4, Tika, POI, Gson
src/main/java/            Plugin implementation
src/main/resources/       Plugin descriptor and icons
src/test/java/            JUnit and IntelliJ fixture tests
src/test/resources/       Synthetic retrieval fixtures
scripts/                  Verification and result inspection
.github/workflows/        CI verification entry
docs/images/              Architecture diagram and historical screenshots
docs/statechart/          Historical serial state model
docs/course-ta-plugin-plan.md
review.md                 Findings, selected options, status, and evidence gaps
```

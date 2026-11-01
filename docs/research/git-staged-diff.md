# Staged Git diff for a commit message action

## Overview

The action reads the staged Git changes for every repository in the project, sends that diff text to a language model, and shows the reply. The repository stays unchanged.

`GitChangeUtils.getStagedChanges` is the staged set. It runs `git diff --name-status --cached` for one repository root. `ChangeListManager` holds the IDE local changes. `GitChangeProvider` fills that list from the work tree. `GitRepository` is the root object that owns the staging holder.

These notes were read on 1 Nov 2026 from the `master` branch of [intellij-community](https://github.com/JetBrains/intellij-community), from the IntelliJ Platform plugin SDK, and from the Git documentation. Which numbered IDE build that `master` commit ships in is unverified.

## Key concepts

`GitRepository` is one Git root. `GitRepositoryManager.getInstance(project).getRepositories()` returns the roots the IDE has mapped. The interface says its getters are fast and can lag the files under `.git` until `update()` runs. `update()` reads disk. Its comment tells callers to keep `update()` off the AWT thread. [GitRepository.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/repo/GitRepository.java)

`GitRepository.getStagingAreaHolder()` returns a `GitStagingAreaHolder`. `getAllRecords()` returns the known tracked status rows for that root. Each `GitFileStatus` stores an index column and a work tree column. One row can be staged, unstaged, or both. Untracked files and ignored files live in other holders. [GitStagingAreaHolder.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/status/GitStagingAreaHolder.java) [GitFileStatus.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitFileStatus.kt)

`GitFileStatus.getStagedStatus()` returns a `FileStatus` for a real index change. It returns null when the index column is ignored, untracked, or part of a conflict. An unchanged index column is the space character, and `getFileStatus` maps that character to null. [GitIndexStatusUtil.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitIndexStatusUtil.kt)

`GitChangeUtils.getStagedChanges(project, root)` returns `GitDiffChange` rows for the index compared with `HEAD`. `getUnstagedChanges` compares the work tree with the index. `getWorkingTreeChanges` compares the work tree with `HEAD`. [GitChangeUtils.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/changes/GitChangeUtils.java)

`GitChangeProvider` is the Git `ChangeProvider`. It reports working-copy changes into `ChangeListManager`. The platform documents `ChangeProvider.getChanges` as work that runs on a background thread. [Version Control Systems](https://plugins.jetbrains.com/docs/intellij/vcs-integration-for-plugins.html) [GitChangeProvider.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/status/GitChangeProvider.java)

`GitChangesCollector` builds the `Change` objects that provider reports. The before side is `HEAD`. The after side uses a null revision, which is the file on disk. A file that is both staged and unstaged still becomes one work-tree change after a `HEAD` diff. The collector class is package-private. [GitChangesCollector.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/status/GitChangesCollector.java)

`ChangeListManager.getAllChanges()` is that local-change list. `areChangeListsEnabled()` is false when Git is the only VCS and the project is in staging-area commit mode. Reads then return one blank default changelist. The empty-index check is separate and is described below. [ChangeListManager.java](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-api/src/com/intellij/openapi/vcs/changes/ChangeListManager.java)

## How it works

### Read the staged set

For each `GitRepository`, call `GitChangeUtils.getStagedChanges(project, repository.getRoot())`. The collection for that root is the staged set. A `GitDiffChange` has a `FileStatus`, a before path, and an after path. It has no hunk text.

Use `GitStagingAreaHolder.getAllRecords()` only as a cached status view. Keep a row when `getStagedStatus()` is non-null. The staging commit panel uses that filter, then `createChange(project, root, status, ContentVersion.HEAD, ContentVersion.STAGED)`. The panel helpers are private. The public pieces are `GitFileStatus.getStagedStatus` and `git4idea.index.createChange`. [GitStageCommitPanel.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/ui/GitStageCommitPanel.kt) [GitStageDiffUtil.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitStageDiffUtil.kt)

`GitStageTracker.State.stagedRoots` is the set of roots whose cached rows have a non-null `getStagedStatus()`. A root state starts uninitialized. An empty tracker before the first refresh does not prove the index is empty. [GitStageTracker.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitStageTracker.kt)

`GitStagingAreaHolder.refresh` is marked `@ApiStatus.Internal`. Leave it to the platform. `GitChangeProvider.getChanges` refreshes status and feeds the changelist builder. The action reads the staged set through `GitChangeUtils` instead.

### Turn the changes into diff text

`IdeaTextPatchBuilder.buildPatch` and `UnifiedDiffWriter.write` turn `Change` objects into unified diff text inside the IDE process. `UnifiedDiffWriter` writes the header in `INDEX_SIGNATURE` and can write the block in `ADDITIONAL_PREFIX`. That string is an IDE patch. The stdout of `git diff` is a different string. [IdeaTextPatchBuilder.java](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/src/com/intellij/openapi/diff/impl/patch/IdeaTextPatchBuilder.java) [UnifiedDiffWriter.java](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/src/com/intellij/openapi/diff/impl/patch/UnifiedDiffWriter.java)

A `Change` from `createChange` between `ContentVersion.HEAD` and `ContentVersion.STAGED` is the staged pair. Loading the `HEAD` bytes calls `GitFileUtils.getFileContent`, and that method runs `GitCommand.CAT_FILE`. Whether the staged index virtual file also starts a Git process is unverified. [GitFileUtils.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/util/GitFileUtils.kt)

Status rows hold a status and paths. The IDE patch writer emits an IDE patch. The action reads hunks by running Git.

Build a `GitLineHandler` for `GitCommand.DIFF` on the repository root. Add `--cached`. Add `-M` so rename detection matches `getStagedChanges`. Leave off `--name-status` and `--name-only`. Run it with `Git.getInstance().runCommand` and take the output. `GitCommand.DIFF` is a read command. `GitCommand.COMMIT` is a write command. The action leaves `COMMIT` unused. [GitCommand.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/commands/GitCommand.java) [Git.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/commands/Git.java)

Git documents `git diff --cached` as the form that views staged changes. With no commit argument, it shows all staged changes. Git documents `--staged` as a synonym of `--cached`. The IDE source passes `--cached`. [git-diff](https://git-scm.com/docs/git-diff)

Run that command once per repository root and concatenate the stdout. Send the concatenated text to the model. Show the reply on the EDT. If `runCommand` fails, show the failure text.

Passing `ChangeListManager` changes to `IdeaTextPatchBuilder` formats the work tree against `HEAD`. A file with both staged and unstaged edits then includes the unstaged hunks.

### No repository, and nothing staged

No Git repository. `GitUtil.hasGitRepositories(project)` returns false. The same fact is an empty list from `GitRepositoryManager.getInstance(project).getRepositories()`. Both are in the reviewed Git API dump. [api-dump.txt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/api-dump.txt) [GitUtil.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/GitUtil.java)

For one file, `GitRepositoryManager.getRepositoryForFile` returns null. `GitUtil.getRepositoryForFile` is annotated `@RequiresBackgroundThread` and throws `VcsException` when the file has no repository. The concrete exception type is a private nested class. Test the empty repository list, or the null from the manager. Leave the private type unnamed.

Nothing staged. `GitChangeUtils.getStagedChanges` returns an empty collection for every root. `GitUtil.hasLocalChanges(true, project, root)` returns false. That helper runs `git diff --name-only --no-renames --cached`.

`GitStagingAreaHolder.isEmpty()` is true when `getAllRecords()` is empty. Unstaged tracked edits are in that list. A dirty work tree with an empty index makes `isEmpty()` false.

Show which case happened. Skip the model call.

### Put the action on the commit message toolbar

Add the action to the group `Vcs.MessageActionGroup`. The group `ChangesView.CommitToolbar` references it. `NonModalCommitPanel` loads `ChangesView.CommitToolbar` beside the commit message. `GitStageCommitPanel` extends `NonModalCommitPanel`, so the same toolbar is present when Git staging mode is on. [VcsActions.xml](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/resources/META-INF/VcsActions.xml) [NonModalCommitPanel.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/src/com/intellij/vcs/commit/NonModalCommitPanel.kt)

The place string passed to `createActionToolbar` is `ChangesView.CommitToolbar`. The constant that holds it is internal to `NonModalCommitPanel`. `ActionPlaces` says `AnActionEvent.getPlace()` is for stats, logging, and debugging. Register with `add-to-group`. [ActionPlaces.java](https://github.com/JetBrains/intellij-community/blob/master/platform/ide-core/src/com/intellij/openapi/actionSystem/ActionPlaces.java)

The commit changes tree installs the popup group `ChangesViewPopupMenuShared`. `ChangesViewPopupMenu` is added into that shared group. Use the popup for an action that runs on the selected row. The message group matches an action that reads every staged file. [CommitChangesViewWithToolbarPanel.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/shared/src/com/intellij/openapi/vcs/changes/CommitChangesViewWithToolbarPanel.kt)

`Git.Stage.Toolbar` and `Git.Stage.Tree.Menu` are the staging file tree toolbar and popup. They are optional extra homes. [intellij.vcs.git.backend.xml](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/resources/intellij.vcs.git.backend.xml) [GitStagePanel.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/ui/GitStagePanel.kt)

An editor action is the wrong home. `ActionPlaces.EDITOR_POPUP` is the string `EditorPopup`. The editor context is the open file. This action reads the index for the project. `Git.Stage.Index.File.Menu` is added to `EditorPopupMenu` for one index file open in an editor. That menu is a different action.

The Git plugin id is `Git4Idea`. Declare that dependency before calling `git4idea` classes. The platform VCS chapter documents `Change` and `ChangeProvider`. The index API lives in the Git plugin. [plugin.xml](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/resources/META-INF/plugin.xml)

Leave the action out of `Vcs.Commit.PrimaryCommitActions`.

### Keep Git off the EDT

`AnAction.update` returns quickly. It can run often on the EDT when the action leaves the update thread at the default. Return `ActionUpdateThread.BGT` from `getActionUpdateThread`. A BGT update may read the project model and leaves Swing alone. An EDT update stays off PSI, VFS, and project data. Call `GitUtil.hasGitRepositories` from the BGT update and hide the action when it returns false. Keep `git diff` out of `update`. [Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)

Long work stays off the EDT. The threading guide moves that work to a background process. Plugins that target 2024.1 and later switch threads with coroutine dispatchers. [Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html) [Background Processes](https://plugins.jetbrains.com/docs/intellij/background-processes.html)

`CommitChangesViewWithToolbarPanel.refreshView` is annotated `@RequiresBackgroundThread` and then moves to `Dispatchers.EDT` to update the tree. Follow that split. Run `GitChangeUtils.getStagedChanges` and the `GitLineHandler` on the background thread. Show the model reply on the EDT.

Whether `Git.runCommand` itself rejects the EDT is unverified. No such check turned up in `Git.java` or in the dispatch-thread test inside `GitHandler.shouldSuppressReadLocks`. The platform rule still keeps the process off the EDT. [GitHandler.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/commands/GitHandler.java)

## Where things live

`GitChangeUtils` lives in `plugins/git4idea/backend/src/changes/GitChangeUtils.java`.

`GitStagingAreaHolder` and `GitChangeProvider` live in `plugins/git4idea/backend/src/status/`.

`GitFileStatus`, `GitStageTracker`, and `createChange` live under `plugins/git4idea/backend/src/index/`.

`GitRepository` and `GitRepositoryManager` live in `plugins/git4idea/backend/src/repo/`.

The commit toolbar group is declared in `platform/vcs-impl/resources/META-INF/VcsActions.xml` and installed by `platform/vcs-impl/src/com/intellij/vcs/commit/NonModalCommitPanel.kt`.

The reviewed Git API list is `plugins/git4idea/backend/api-dump.txt`. The unreviewed list is `plugins/git4idea/backend/api-dump-unreviewed.txt`.

## Gotchas

`GitUtil.hasGitRepositories` and `GitRepositoryManager.getRepositories` are in the reviewed dump. `getStagedChanges`, `getStagingAreaHolder`, `getAllRecords`, `getStagedStatus`, `hasLocalChanges`, and `createChange` are in the unreviewed dump. A later IDE build can change those signatures. [api-dump-unreviewed.txt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/api-dump-unreviewed.txt)

`getStagedStatus()` returns null for a conflicted row. The `git diff --cached` command ignores that filter. Whether a conflicted path appears in that command's stdout is unverified here. Use the command for text that matches what Git has staged.

`GitIndexUtil.listStaged` runs `git ls-files -s` for the paths you pass. The result is index entries, including entries that match `HEAD`.

`GitRepository` getters can be stale. The change-provider refresh fills the staging holder. The action leaves `refresh` alone.

A project can contain more than one root. Check every root before treating the index as empty.

## Sources

- [GitChangeUtils.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/changes/GitChangeUtils.java)
- [GitStagingAreaHolder.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/status/GitStagingAreaHolder.java)
- [GitFileStatus.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitFileStatus.kt)
- [GitIndexStatusUtil.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitIndexStatusUtil.kt)
- [GitChangeProvider.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/status/GitChangeProvider.java)
- [GitChangesCollector.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/status/GitChangesCollector.java)
- [ChangeListManager.java](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-api/src/com/intellij/openapi/vcs/changes/ChangeListManager.java)
- [GitRepository.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/repo/GitRepository.java)
- [GitUtil.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/GitUtil.java)
- [GitStageTracker.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitStageTracker.kt)
- [GitStageCommitPanel.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/ui/GitStageCommitPanel.kt)
- [GitStageDiffUtil.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/GitStageDiffUtil.kt)
- [GitFileUtils.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/util/GitFileUtils.kt)
- [IdeaTextPatchBuilder.java](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/src/com/intellij/openapi/diff/impl/patch/IdeaTextPatchBuilder.java)
- [UnifiedDiffWriter.java](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/src/com/intellij/openapi/diff/impl/patch/UnifiedDiffWriter.java)
- [GitCommand.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/commands/GitCommand.java)
- [Git.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/commands/Git.java)
- [GitHandler.java](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/commands/GitHandler.java)
- [VcsActions.xml](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/resources/META-INF/VcsActions.xml)
- [NonModalCommitPanel.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/src/com/intellij/vcs/commit/NonModalCommitPanel.kt)
- [CommitChangesViewWithToolbarPanel.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/vcs-impl/shared/src/com/intellij/openapi/vcs/changes/CommitChangesViewWithToolbarPanel.kt)
- [ActionPlaces.java](https://github.com/JetBrains/intellij-community/blob/master/platform/ide-core/src/com/intellij/openapi/actionSystem/ActionPlaces.java)
- [intellij.vcs.git.backend.xml](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/resources/intellij.vcs.git.backend.xml)
- [GitStagePanel.kt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/src/index/ui/GitStagePanel.kt)
- [plugin.xml](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/resources/META-INF/plugin.xml)
- [api-dump.txt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/api-dump.txt)
- [api-dump-unreviewed.txt](https://github.com/JetBrains/intellij-community/blob/master/plugins/git4idea/backend/api-dump-unreviewed.txt)
- [Version Control Systems](https://plugins.jetbrains.com/docs/intellij/vcs-integration-for-plugins.html)
- [Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)
- [Threading Model](https://plugins.jetbrains.com/docs/intellij/threading-model.html)
- [Background Processes](https://plugins.jetbrains.com/docs/intellij/background-processes.html)
- [git-diff](https://git-scm.com/docs/git-diff)

# 课程助教插件计划

B 组要做的是一个 IntelliJ IDEA 插件。它根据课件回答问题，并加上三个智能动作。队伍在 IDE 里演示这个插件。范围是 F0 和 F1。不做 F2。PR 顺序是 PR-1、PR-2、PR-3、PR-4、PR-5，然后 PR-6。

英文执行稿是 [course-ta-plugin-plan.md](course-ta-plugin-plan.md)。检查脚本只认那份英文标题。这份是阅读稿。

## 怎么读这份计划

一个框是一份工作。每个框都写出用来核对的证据。嵌套框是上一框的子步骤。证据要是文件、日志行、截图、一次测试或一个 SHA，才能勾上。正文是操作说明。附录负责解释和记录。

程序按 `playbooks/autopilot-stack.md` 跑。每个 PR 由你来落地。负责人做到可合并就停。PR-1 到 PR-6 都停在可合并，等你落地。

只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

## 程序清单

### 武装程序

- [ ] 把协议和这份计划告诉操作者，然后停。只有操作者明确说开始，才开始执行。
- [ ] 程序开始时从主干读下面这些文件。每一拍都重读。
  - [ ] `git show origin/main:pstack/skills/poteto-mode/playbooks/autopilot-stack.md`
  - [ ] `git show origin/main:pstack/skills/swarm/SKILL.md`
  - [ ] `git show origin/main:cursor-team-kit/skills/control-ui/SKILL.md`
  - [ ] `git show origin/main:pstack/skills/poteto-mode/playbooks/opening-a-pr.md`
  - [ ] `git show origin/main:pstack/skills/poteto-mode/playbooks/prototype.md`
- [ ] 操作者说开始之后，把审计拍子设成 `/loop 1h`，提示词用下面这一段。不要靠记忆记间隔。
- [ ] 提示词按英文原文使用，不要改写。原文如下。

```text
Re-read the execution playbook from trunk. Audit the operation against it and fix drift in this tick. Probe every active lane and judge progress by side effects only. Stand down a stuck lane and dispatch its replacement now. Then post a short status message to the operator in chat only when the audit found a tracked change that no earlier status message reported, such as a PR opened, a code-ready head, a round launched or closed, a verdict, a merge, a stuck agent and the action taken, a blocker added or cleared, or a decision only the operator can make. Name every such change and nothing else. Do not repeat a table, the merged list, or an unchanged blocker. If the audit found none, end the turn with no reply text. Either way, log this tick's row in your decision trail. The row names the items reported, or none.
```

- [ ] 操作者喊暂停或撤下时，立刻给每个负责人发禁止写入的命令。

### 派出负责人

- [ ] 每个 PR 派一个负责人，生命周期按执行手册来。
- [ ] 按这张依赖图做。父分支存在之后才开始子工作，子分支基于父分支。
  - [ ] PR-1 最先，从 `main` 分出。
  - [ ] PR-2 在 PR-1 之后。
  - [ ] PR-3 在 PR-2 之后。
  - [ ] PR-4 在 PR-3 之后。
  - [ ] PR-5 在 PR-4 之后。
  - [ ] PR-6 在 PR-5 之后。
- [ ] 守住文件边界。每个 PR 只改自己「文件」栏里的路径。注册一个新动作或一个新工具窗口时，可以再改 `src/main/resources/META-INF/plugin.xml`。
- [ ] 守住审阅门。PR-1 到 PR-6 都改界面。合并前要等操作者在对话里看过截图和录像。

### 每个 PR 的机制

- [ ] 先定一次代码托管。默认用 `gh`。若 `command -v origin` 成功，并且 Origin 能解析这个仓库，则所有 PR 操作改用 `origin pr`。退回 `gh` 时要记下来。不要依赖 `gt`。
- [ ] PR 打开时就是可审阅状态，不要开成草稿。按「打开 PR」那份手册做。运行环境自带 PR 工具就用那个。否则按已经选定的托管，用 `origin pr create --status open --base <base-branch>` 或 `gh pr create --base <base-branch>`。叠放的子 PR 以父分支为基。
- [ ] 面向 PR 的推送之前，跑一次仓库的 lint 和类型检查。推送时打开钩子。
- [ ] 每次提交前跑 `/deslop`。审阅前跑 `/no-comments`。
- [ ] Bugbot 和安全审阅的每条评论都按 `../references/bugbot-triage.md` 分流。
- [ ] 代码就绪报告和后续照看之前，变基到父分支。栈底变基到当前主干。修复轮次保持这个合并基。只在准备合并、和父分支发生 `git merge-tree` 冲突、父分支改动导致 CI 失败、或已知的并发改动落到同一批文件时，才再次变基。

### 每个 PR 的裁决与合并

- [ ] 完整蜂群在代码就绪的那个提交上跑一次。只有新行为、重做设计或一大块新差异，才再跑一轮完整蜂群。变基、解冲突或小修复，在新提交上派一个核对者。只改措辞则不派。完整蜂群按 `pstack/skills/swarm/SKILL.md` 跑。一条门禁通道。十条现场通道来自该 PR 的「现场验证」。一条性能通道来自「性能验证」。至少两条审计通道，各自有重点，读差异和收据，不轻信 PR 正文。根负责人在裁决前审计合并就绪报告里的收据。
- [ ] 每条发现按执行手册的阻断规则判断。没有阻断项，裁决才算干净。标成 `Regression lane against trunk.` 的通道如果失败在主干行为上，记在主干名下，不阻断这个 PR。新提交要重新裁决。`playbooks/shipping.md` 里 patch-id 规则仍认可的结果除外。
- [ ] PR 在当前提交上验证通过并且是绿的，就交给操作者。不要合并。交出之后，没有操作者的允许，任何人不再推送。重新叠放后 `git patch-id` 不变的，保留原裁决。整条链都有干净裁决之后，操作者才从下往上落地。

### 每条现场通道的启动步骤

每条现场通道在 PR 头的一台云虚拟机上跑。这个 IDE 没有 `control-ui` 或 `control-cli` 驱动。无界面断言用 `./gradlew test`。窗口用 `./gradlew runIde` 启动沙箱，再按名字截图。缺驱动的事记在附录 C。

- [ ] `git fetch origin <head-branch> && git checkout <head SHA>`。
- [ ] 安装 JDK 25。跑 `./gradlew test`，等到任务打印 `BUILD SUCCESSFUL` 或某个失败测试的名字。窗口通道跑 `./gradlew runIde`，等到日志打印 `Course TA tool window created`。
- [ ] 输入只通过上面的 Gradle 任务送入。只读诊断是 `build/reports/tests` 下的测试 XML 和沙箱日志。不要用没有记录的点击去操作 IDE。
- [ ] 每张截图存到 `/tmp/swarm-<pr-id>/worker-<n>/<slug>.png`，并随报告返回路径。

## 搭起插件空壳（PR-1）

**依赖。** 无。

**文件。**

- [ ] 创建 `settings.gradle.kts`。
- [ ] 创建 `build.gradle.kts`。
- [ ] 创建 `gradle/wrapper/gradle-wrapper.properties`。
- [ ] 创建 `src/main/resources/META-INF/plugin.xml`。
- [ ] 创建 `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`。
- [ ] 创建 `src/main/java/cst3492/courseta/TaSettings.java`。
- [ ] 创建 `src/test/java/cst3492/courseta/TaSettingsTest.java`。

**构建。**

- [ ] 在 `build.gradle.kts` 里应用插件 id `org.jetbrains.intellij.platform`，版本 `2.19.0`。
- [ ] 依赖 `intellijIdea("2026.2.4")` 和 `bundledPlugin("com.intellij.java")`。
- [ ] `sinceBuild` 设为 `262`。`untilBuild` 设为 `262.*`。两边都不要加产品代码前缀。
- [ ] 本 PR 开始时从 Gradle 发布页解析一个 Gradle 9 的 wrapper。不要提前编补丁号。
- [ ] 把 `CourseTaToolWindowFactory` 注册到 `com.intellij.toolWindow`，id 为 `Course TA`。
- [ ] 基址和模型名放进 `TaSettings`，类型是 `PersistentStateComponent`。API key 只通过 `PasswordSafe` 保存，并且离开 EDT。

**你会看到。**

- [ ] 沙箱里出现 `Course TA` 工具窗口。正文写着还没有索引课件。
- [ ] 设置里基址和模型名为空。密钥框不回显已保存的密钥。

**单元验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] `TaSettingsTest` 保存一个模型名，并断言导出的状态 XML 里没有 API key。运行 `./gradlew test`。

**现场验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。十条通道在 PR 头上使用 `grok-4.7-xhigh-fast`，按上面的启动步骤跑。

- [ ] 通道 1。对照主干的回归通道。主干跑空工作区，头跑沙箱。若主干没有这个功能，记下这一点，并检查 `Course TA` 窗口和用户等待的空闲面板。保存 `pr1-trunk-absent.png`。头上出现 `Course TA`，主干没有这个窗口，才算通过。
- [ ] 通道 2。从工具窗口条打开窗口。保存 `pr1-open-window.png`。标题是 `Course TA`，正文写着还没有索引课件，才算通过。
- [ ] 通道 3。打开设置。保存 `pr1-settings-empty.png`。基址、模型名、密钥三栏都可见且为空，才算通过。
- [ ] 通道 4。填入基址和模型名，重启沙箱，再打开设置。保存 `pr1-settings-persist.png`。两个值还在，密钥栏仍为空，才算通过。
- [ ] 通道 5。存入密钥，重启，把导出的设置 XML 和设置页放在一起看。保存 `pr1-key-not-in-xml.png`。XML 里没有密钥文本，页面显示密钥已保存，才算通过。
- [ ] 通道 6。把工具窗口拉窄。保存 `pr1-narrow.png`。设置控件仍然可见，才算通过。
- [ ] 通道 7。密钥留空，看 Ask 控件。保存 `pr1-ask-disabled.png`。Ask 不可用，提示写出缺的是密钥，才算通过。
- [ ] 通道 8。关掉工具窗口再打开。保存 `pr1-reopen.png`。空闲文案还在，才算通过。
- [ ] 通道 9。运行 `./gradlew test` 并打开 HTML 报告。保存 `pr1-test-report.png`。任务打印 `BUILD SUCCESSFUL`，才算通过。
- [ ] 通道 10。读启动后的沙箱日志。保存 `pr1-plugin-loaded.png`。日志写出插件名，并且没有缺失 `com.intellij.java` 依赖，才算通过。

**性能验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] 指标。从 `runIde` 进程启动到日志打印 `Course TA tool window created` 的秒数，以及用户等待的空闲面板。主干没有插件，所以记下主干没有这行日志。
- [ ] 探测。先启动主干，再启动头，再启动主干。在头上读工厂日志。在主干上记录缺失。
- [ ] 基线。先记录主干的缺失。
- [ ] 规则。头上没有这行日志，或这行日志出现在进程启动 90 秒之后，则失败。不要用头除以主干。90 秒是冷启动沙箱的未测量上限，不是这份计划已经测到的时间。

**审阅门。** 合并前由操作者审阅。

- [ ] 把通道 2 的截图复制到 `/cursor/stores/self/docs/media/pr-1-review-window.png`。
- [ ] 在通道虚拟机上录 30 到 60 秒，内容是打开工具窗口和设置页。存为 `/cursor/stores/self/docs/media/pr-1-review.mp4`。
- [ ] 把截图和录像发到对话里。停在可合并。等待操作者审阅。

**合并。**

- [ ] 根负责人对确切的头 SHA 给出干净裁决。
- [ ] Bugbot 分流完成。
- [ ] 裁决之后变基到当前主干，patch-id 不变。
- [ ] 负责人不合并。栈就绪后，由操作者落地 PR-1。

## 索引课件（PR-2）

**依赖。** PR-1。

**文件。**

- [ ] 创建 `src/main/java/cst3492/courseta/index/CourseChunk.java`。
- [ ] 创建 `src/main/java/cst3492/courseta/index/CourseIndex.java`。
- [ ] 创建 `src/main/java/cst3492/courseta/index/CourseFileParser.java`。
- [ ] 创建 `src/test/java/cst3492/courseta/index/CourseIndexTest.java`。
- [ ] 创建 `src/test/resources/fixture-two-page.pdf`。
- [ ] 修改 `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`。
- [ ] 修改 `build.gradle.kts`。

**构建。**

- [ ] 增加 `CourseChunk`。字段是 `id`、`sourceDocument`、取值为 `PAGE` 或 `SLIDE` 的 `scopeKind`、从 1 开始的 `scopeNumber`，以及 `text`。
- [ ] 用 Tika `4.2.0` 的 `tika-core`、`tika-parser-pdf-module`、`tika-parser-microsoft-module` 解析。把插件类加载器传给 `DefaultParser`。`includeSlideMasterContent` 设为 false。
- [ ] PDF 里每个 class 为 `page` 的 `div` 存一块。PPTX 里每个 class 为 `slide-content` 的 `div` 存一块。空正文丢掉。
- [ ] 用进程内词表排序。索引 ASCII 单词和二字组。词块表写到插件配置目录，启动时读回。
- [ ] 增加 Index files 按钮。它把选中的文件复制进插件语料，并重建词表。

**你会看到。**

- [ ] 索引两页夹具之后，工具窗口列出两行。每行有文件路径和页码。
- [ ] 与第 2 页共享一个二字组的中文查询会高亮那一行，不会高亮无关页。

**单元验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] `CourseIndexTest` 索引一对夹具字符串，并断言中文查询只返回匹配的块 id。运行 `./gradlew test`。

**现场验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。十条通道在 PR 头上使用 `grok-4.7-xhigh-fast`，按上面的启动步骤跑。

- [ ] 通道 1。对照主干的回归通道。在主干和头上做索引。若主干没有这个功能，记下这一点，并检查两行列表，以及索引前用户等待的空闲面板。保存 `pr2-trunk-absent.png`。头上列出两行，主干没有 Index files 按钮，才算通过。
- [ ] 通道 2。索引 `fixture-two-page.pdf`。保存 `pr2-two-pages.png`。两行分别显示第 1 页和第 2 页，才算通过。
- [ ] 通道 3。索引一份两张幻灯片的 PPTX 夹具。保存 `pr2-two-slides.png`。两行分别显示第 1 张和第 2 张，才算通过。
- [ ] 通道 4。用只出现在第 2 页的中文查询。保存 `pr2-chinese-hit.png`。第 2 页是第一行，才算通过。
- [ ] 通道 5。用只出现在另一夹具页的英文词查询。保存 `pr2-english-hit.png`。那一页是第一行，才算通过。
- [ ] 通道 6。再次索引同一份 PDF。保存 `pr2-reindex.png`。行数仍是两行，才算通过。
- [ ] 通道 7。索引一页抽出正文为空的 PDF。保存 `pr2-empty-page.png`。那一页没有行，才算通过。
- [ ] 通道 8。重启沙箱并重新打开工具窗口。保存 `pr2-restart.png`。同样的两行还在，而且没有新的索引动作，才算通过。
- [ ] 通道 9。用匹配不到任何块的词查询。保存 `pr2-no-hit.png`。列表写明没有课件块命中，才算通过。
- [ ] 通道 10。索引一份 PPTX。母版里有一个任何幻灯片都没有的词。保存 `pr2-no-master.png`。查询这个词没有行，才算通过。

**性能验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] 指标。`CourseIndex.index` 处理 `fixture-two-page.pdf` 的毫秒数，以及用户等待的两行。主干没有索引器，所以记下主干不能跑这个方法。
- [ ] 探测。在头上用一个小测试具调用这个方法。沙箱启动前后各跑一次。主干记为方法不存在。
- [ ] 基线。先记录主干的缺失。
- [ ] 规则。这一夹具的一次索引超过 2000 毫秒，则头失败。不要用头除以主干。2000 毫秒是未测量上限，不是这份计划已经测到的时间。

**审阅门。** 合并前由操作者审阅。

- [ ] 把通道 2 的截图复制到 `/cursor/stores/self/docs/media/pr-2-review-rows.png`。
- [ ] 在通道虚拟机上录 30 到 60 秒，内容是索引夹具并做中文查询。存为 `/cursor/stores/self/docs/media/pr-2-review.mp4`。
- [ ] 把截图和录像发到对话里。停在可合并。等待操作者审阅。

**合并。**

- [ ] 根负责人对确切的头 SHA 给出干净裁决。
- [ ] Bugbot 分流完成。
- [ ] 裁决之后变基到 PR-1，patch-id 不变。
- [ ] 负责人不合并。操作者只在 PR-1 之后落地 PR-2。

## 根据课件回答（PR-3）

**依赖。** PR-2。

**文件。**

- [ ] 创建 `src/main/java/cst3492/courseta/ask/Answer.java`。
- [ ] 创建 `src/main/java/cst3492/courseta/ask/ChatClient.java`。
- [ ] 创建 `src/main/java/cst3492/courseta/ask/AskAction.java`。
- [ ] 创建 `src/test/java/cst3492/courseta/ask/AnswerTest.java`。
- [ ] 修改 `src/main/resources/META-INF/plugin.xml`。
- [ ] 修改 `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`。

**构建。**

- [ ] 增加 `Answer`。种类是 `CITED` 或 `GENERAL`，加上回答文本，以及检索到的 `CourseChunk` 列表。
- [ ] 在后台线程检索，先把块的路径和范围画到工具窗口，再向配置的聊天地址 POST `messages`。响应体符合该形状时，读取 `choices[0].message.content`。
- [ ] 命中列表为空时，不编造引用。显示这句话，`Response is based on general knowledge; no specific course material is referenced.`
- [ ] 把 `AskAction` 注册到 `EditorPopupMenu`。选区从主光标的偏移读取。用 `PsiFile.findElementAt`，再用 `PsiTreeUtil.getParentOfType` 找到 `PsiMethod`。
- [ ] `getActionUpdateThread` 返回 `ActionUpdateThread.BGT`。

**你会看到。**

- [ ] 有命中的问题，在任何回答文本到来之前，先显示路径和页码或幻灯片编号。
- [ ] 在选中的方法上右键，会针对这段选区提问，并且仍然显示这些引用。

**单元验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] `AnswerTest` 用空命中列表构造一个 `GENERAL` 回答，断言那句固定英文，并且没有挂上任何块。运行 `./gradlew test`。

**现场验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。十条通道在 PR 头上使用 `grok-4.7-xhigh-fast`，按上面的启动步骤跑。

- [ ] 通道 1。对照主干的回归通道。在主干和头上提问。若主干没有这个功能，记下这一点，并检查引用行和用户等待的回答文本。保存 `pr3-trunk-absent.png`。头上出现引用，主干没有 Ask 动作，才算通过。
- [ ] 通道 2。提一个能命中某一夹具页的问题。聊天客户端桩延迟两秒。保存 `pr3-citation-first.png`。路径和页码在桩返回之前可见，才算通过。
- [ ] 通道 3。选中一个方法，用编辑器右键的 Ask。保存 `pr3-right-click.png`。问题面板含有选中文本和一行引用，才算通过。
- [ ] 通道 4。提一个没有命中块的问题。保存 `pr3-general.png`。面板显示通用知识那句英文，并且没有引用行，才算通过。
- [ ] 通道 5。去掉 API key，再提一个能命中的问题。保存 `pr3-no-key.png`。引用行可见，面板写出缺密钥，而不是写出回答，才算通过。
- [ ] 通道 6。把基址指到一个关闭的端口。保存 `pr3-http-down.png`。引用行还在，面板显示 HTTP 失败，才算通过。
- [ ] 通道 7。在桩成功返回之后读一行引用。保存 `pr3-cite-fields.png`。这一行同时有课程相对路径和范围编号，才算通过。
- [ ] 通道 8。再提一个命中另一页的问题。保存 `pr3-second-question.png`。引用列表只有第二页，才算通过。
- [ ] 通道 9。在空选区上右键。保存 `pr3-empty-selection.png`。面板写明没有选中代码，并且没有记录到 HTTP 调用，才算通过。
- [ ] 通道 10。提一个与代码无关的问题。保存 `pr3-non-code.png`。检索仍然运行，面板要么有引用，要么有通用知识那句英文，才算通过。

**性能验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] 指标。从提交到画出引用行的毫秒数，不含 HTTP 往返，以及桩返回后用户等待的回答文本。主干没有 Ask 动作，所以记下这个缺失。
- [ ] 探测。在头上使用桩客户端。先启动主干并记录动作缺失。然后在头上做两次计时提交。
- [ ] 基线。先记录主干的缺失。
- [ ] 规则。在夹具索引上，引用行出现在提交之后超过 500 毫秒，则头失败。不要用头除以主干。500 毫秒是本地检索的未测量上限，不是这份计划已经测到的时间。

**审阅门。** 合并前由操作者审阅。

- [ ] 把通道 2 的截图复制到 `/cursor/stores/self/docs/media/pr-3-review-citation.png`。
- [ ] 在通道虚拟机上录 30 到 60 秒，内容是一次右键提问和一次没有命中的提问。存为 `/cursor/stores/self/docs/media/pr-3-review.mp4`。
- [ ] 把截图和录像发到对话里。停在可合并。等待操作者审阅。

**合并。**

- [ ] 根负责人对确切的头 SHA 给出干净裁决。
- [ ] Bugbot 分流完成。
- [ ] 裁决之后变基到 PR-2，patch-id 不变。
- [ ] 负责人不合并。操作者只在 PR-2 之后落地 PR-3。

## 按场景生成单元测试（PR-4）

**依赖。** PR-3。

**文件。**

- [ ] 创建 `src/main/java/cst3492/courseta/junitgen/GenerateTestAction.java`。
- [ ] 创建 `src/test/java/cst3492/courseta/junitgen/GenerateTestActionTest.java`。
- [ ] 修改 `src/main/resources/META-INF/plugin.xml`。
- [ ] 修改 `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`。

**构建。**

- [ ] 把 `GenerateTestAction` 注册到 `EditorPopupMenu`，对象是选中的 `PsiClass` 或 `PsiMethod`。
- [ ] 先打开文本框。文本框不是非空文本时，不调用 `ChatClient`。
- [ ] 把场景文本和选中的源码发给 `ChatClient`。把返回的 JUnit 源码显示在工具窗口。
- [ ] 增加 Insert 按钮。它通过写命令把预览写入一个新的测试文件。这次点击之前不写文件。

**你会看到。**

- [ ] 动作之后的第一块面板是场景文本框，此时还没有测试源码。
- [ ] 桩返回之后，预览里有一个点名 JUnit 的类。在 Insert 之前，编辑器不变。

**单元验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] `GenerateTestActionTest` 断言空场景不会调用聊天客户端。运行 `./gradlew test`。

**现场验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。十条通道在 PR 头上使用 `grok-4.7-xhigh-fast`，按上面的启动步骤跑。

- [ ] 通道 1。对照主干的回归通道。在主干和头上跑这个动作。若主干没有这个功能，记下这一点，并检查场景框和用户等待的预览。保存 `pr4-trunk-absent.png`。头上出现文本框，主干没有这个动作，才算通过。
- [ ] 通道 2。选中一个方法并调用动作。保存 `pr4-scenario-first.png`。场景框获得焦点，并且看不到测试源码，才算通过。
- [ ] 通道 3。提交空场景。保存 `pr4-empty-scenario.png`。没有记录到聊天请求，才算通过。
- [ ] 通道 4。提交一个场景，桩返回一个 JUnit 类。保存 `pr4-preview.png`。预览含有这个桩类，编辑器文件不变，才算通过。
- [ ] 通道 5。点击 Insert。保存 `pr4-inserted.png`。新测试文件被打开，文本与预览一致，才算通过。
- [ ] 通道 6。调用动作，不点 Insert 就关掉文本框。保存 `pr4-cancel.png`。没有新文件，才算通过。
- [ ] 通道 7。选中一个类而不是方法，然后生成。保存 `pr4-class-selection.png`。预览出现，请求里含有该类源码，才算通过。
- [ ] 通道 8。没有选区时调用动作。保存 `pr4-no-selection.png`。面板要求选中一个类或一个方法，并且没有聊天请求，才算通过。
- [ ] 通道 9。读预览里的 import 行。保存 `pr4-junit-import.png`。预览含有 `org.junit`，才算通过。
- [ ] 通道 10。插入一次，再调用动作，然后取消。保存 `pr4-second-cancel.png`。第一次插入的文件不变，才算通过。

**性能验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] 指标。从非空场景提交到画出预览的毫秒数。`ChatClient` 桩立即返回。再加上用户等待的预览。主干没有这个动作，所以记下缺失。
- [ ] 探测。在头上把桩提交跑两次。第一次头运行之前，把主干记为动作不存在。
- [ ] 基线。先记录主干的缺失。
- [ ] 规则。预览出现在桩返回之后超过 500 毫秒，则头失败。不要用头除以主干。500 毫秒是未测量上限，不是这份计划已经测到的时间。

**审阅门。** 合并前由操作者审阅。

- [ ] 把通道 4 的截图复制到 `/cursor/stores/self/docs/media/pr-4-review-preview.png`。
- [ ] 在通道虚拟机上录 30 到 60 秒，内容是场景、预览和 Insert。存为 `/cursor/stores/self/docs/media/pr-4-review.mp4`。
- [ ] 把截图和录像发到对话里。停在可合并。等待操作者审阅。

**合并。**

- [ ] 根负责人对确切的头 SHA 给出干净裁决。
- [ ] Bugbot 分流完成。
- [ ] 裁决之后变基到 PR-3，patch-id 不变。
- [ ] 负责人不合并。操作者只在 PR-3 之后落地 PR-4。

## 起草提交说明（PR-5）

**依赖。** PR-4。

**文件。**

- [ ] 创建 `src/main/java/cst3492/courseta/commit/CommitMessageAction.java`。
- [ ] 创建 `src/test/java/cst3492/courseta/commit/CommitMessageActionTest.java`。
- [ ] 修改 `src/main/resources/META-INF/plugin.xml`。
- [ ] 修改 `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`。

**构建。**

- [ ] 依赖插件 id `Git4Idea`。
- [ ] `CommitMessageAction` 的 `getActionUpdateThread` 返回 `ActionUpdateThread.BGT`。不要在 `update` 里跑 `git diff`。
- [ ] 在后台线程上，把空的 `GitRepositoryManager.getRepositories()` 当成没有仓库。把每个根上的空 `GitChangeUtils.getStagedChanges` 当成没有暂存内容。两种情况都只显示这个事实，不调用 `ChatClient`。
- [ ] 某个根有暂存变更时，用 `GitLineHandler` 跑 `GitCommand.DIFF`，参数是 `--cached` 和 `-M`。拼接标准输出。不调用 `GitCommand.COMMIT`。三条候选在 EDT 上绘制。
- [ ] 让模型给出三条 conventional commit 候选。三条都显示。复制把其中一条放进剪贴板。`Course TA` 窗口里的按钮和注册到 `Vcs.MessageActionGroup` 的动作是同一个。

**你会看到。**

- [ ] 有一个已暂存文件的仓库会显示三条说明候选，以及暂存的 diff 文本。
- [ ] 复制之后，`git status` 仍显示该文件已暂存。

**单元验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] `CommitMessageActionTest` 喂入空的暂存列表，并断言没有调用聊天客户端。运行 `./gradlew test`。

**现场验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。十条通道在 PR 头上使用 `grok-4.7-xhigh-fast`，按上面的启动步骤跑。

- [ ] 通道 1。对照主干的回归通道。在主干和头上跑这个动作。若主干没有这个功能，记下这一点，并检查三条候选和用户等待的仍暂存文件。保存 `pr5-trunk-absent.png`。头上出现三条候选，主干没有这个动作，才算通过。
- [ ] 通道 2。在沙箱仓库里暂存一行，从工具窗口运行动作。保存 `pr5-three-messages.png`。三条候选都非空且可见，才算通过。
- [ ] 通道 3。在干净索引上运行动作。保存 `pr5-nothing-staged.png`。面板写明没有暂存内容，并且没有聊天请求，才算通过。
- [ ] 通道 4。打开一个不是 Git 仓库的项目。保存 `pr5-no-repo.png`。面板写明项目没有 Git 仓库，并且没有聊天请求，才算通过。
- [ ] 通道 5。修改一个文件但不暂存。保存 `pr5-unstaged.png`。面板写明没有暂存内容，才算通过。
- [ ] 通道 6。复制第一条候选。保存 `pr5-copied.png`。剪贴板等于这条候选，才算通过。
- [ ] 通道 7。动作之后读 `git status`。保存 `pr5-still-staged.png`。该文件仍暂存，并且没有新提交，才算通过。
- [ ] 通道 8。从提交说明工具栏运行同一个动作。保存 `pr5-toolbar.png`。同样的三条候选出现，才算通过。
- [ ] 通道 9。让 diff 命令失败。保存 `pr5-diff-failed.png`。面板显示命令失败，并且没有候选，才算通过。
- [ ] 通道 10。桩返回一段不符合 conventional commit 形状的文本。保存 `pr5-free-form.png`。面板仍然显示返回的文本，才算通过。

**性能验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] 指标。为一行已暂存改动收集 `git diff --cached` 文本的毫秒数，以及桩返回后用户等待的三条候选。主干没有这个动作，所以记下缺失。
- [ ] 探测。在头上为 diff 收集计时两次。第一次头运行之前，把主干记为动作不存在。
- [ ] 基线。先记录主干的缺失。
- [ ] 规则。收集这一行 diff 超过 2000 毫秒，则头失败。不要用头除以主干。2000 毫秒是未测量上限，不是这份计划已经测到的时间。

**审阅门。** 合并前由操作者审阅。

- [ ] 把通道 2 的截图复制到 `/cursor/stores/self/docs/media/pr-5-review-messages.png`。
- [ ] 在通道虚拟机上录 30 到 60 秒，内容是一次暂存编辑、三条候选，以及文件仍然暂存。存为 `/cursor/stores/self/docs/media/pr-5-review.mp4`。
- [ ] 把截图和录像发到对话里。停在可合并。等待操作者审阅。

**合并。**

- [ ] 根负责人对确切的头 SHA 给出干净裁决。
- [ ] Bugbot 分流完成。
- [ ] 裁决之后变基到 PR-4，patch-id 不变。
- [ ] 负责人不合并。操作者只在 PR-4 之后落地 PR-5。

## 加上课程提问（PR-6）

**依赖。** PR-5。

**文件。**

- [ ] 创建 `src/main/java/cst3492/courseta/drill/CourseDrillAction.java`。
- [ ] 创建 `src/test/java/cst3492/courseta/drill/CourseDrillActionTest.java`。
- [ ] 修改 `src/main/resources/META-INF/plugin.xml`。
- [ ] 修改 `src/main/java/cst3492/courseta/CourseTaToolWindowFactory.java`。

**构建。**

- [ ] 把 `CourseDrillAction` 注册到 `EditorPopupMenu`。要求选区非空。
- [ ] 先按选区检索块，再让 `ChatClient` 针对这段选区出三个短问题。每个问题带上块的路径和范围。不要写入编辑器。
- [ ] 命中列表为空时，显示同一句通用知识英文，不编造幻灯片编号。
- [ ] 在操作者替换它之前，保持这个动作。另外两个候选记在附录 A 和附录 B。

**你会看到。**

- [ ] 选中一个方法会得到三个问题。有块命中时，每个问题显示路径和范围编号。
- [ ] 动作之后的编辑器文本与动作之前相同。

**单元验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] `CourseDrillActionTest` 断言空选区不会调用聊天客户端。运行 `./gradlew test`。

**现场验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。十条通道在 PR 头上使用 `grok-4.7-xhigh-fast`，按上面的启动步骤跑。

- [ ] 通道 1。对照主干的回归通道。在主干和头上跑提问动作。若主干没有这个功能，记下这一点，并检查三个问题和用户等待的未改编辑器。保存 `pr6-trunk-absent.png`。头上出现三个问题，主干没有这个动作，才算通过。
- [ ] 通道 2。选中一个能命中夹具页的方法，用桩运行提问。保存 `pr6-three-questions.png`。三个问题可见，才算通过。
- [ ] 通道 3。读第一个问题下面的引用。保存 `pr6-question-cite.png`。这条引用有路径和范围编号，才算通过。
- [ ] 通道 4。在没有选区时运行提问。保存 `pr6-no-selection.png`。面板写出缺少选区，并且没有聊天请求，才算通过。
- [ ] 通道 5。在任何索引存在之前运行提问。保存 `pr6-no-index.png`。面板显示通用知识那句英文，才算通过。
- [ ] 通道 6。比较提问前后的编辑器缓冲区。保存 `pr6-editor-unchanged.png`。缓冲区文本不变，才算通过。
- [ ] 通道 7。选中另一个方法，再跑一次。保存 `pr6-second-selection.png`。请求文本含有第二次选区，并且不只含第一次，才算通过。
- [ ] 通道 8。强制空命中列表。保存 `pr6-general.png`。不显示范围编号，才算通过。
- [ ] 通道 9。确认问题下面没有再画一段长回答。保存 `pr6-questions-only.png`。面板有三个问题，没有第二块回答，才算通过。
- [ ] 通道 10。重启沙箱，对同一选区再跑提问。保存 `pr6-after-restart.png`。三个问题再次出现，才算通过。

**性能验证。** 只靠测试不算验证。一个 PR 要在单元、现场、性能三栏都勾上，才算验证过。

- [ ] 指标。从调用提问到屏幕上留出三个问题空位的毫秒数。这发生在桩返回之前。再加上用户等待的三个问题。主干没有这个动作，所以记下缺失。
- [ ] 探测。桩延迟时，在头上为这次绘制计时两次。第一次头运行之前，把主干记为动作不存在。
- [ ] 基线。先记录主干的缺失。
- [ ] 规则。调用之后 500 毫秒内没有留出三个空位，则头失败。空位要在桩返回之前出现。不要用头除以主干。500 毫秒是未测量上限，不是这份计划已经测到的时间。

**审阅门。** 合并前由操作者审阅。

- [ ] 把通道 2 的截图复制到 `/cursor/stores/self/docs/media/pr-6-review-questions.png`。
- [ ] 在通道虚拟机上录 30 到 60 秒，内容是一次选区、三个问题，以及编辑器未改。存为 `/cursor/stores/self/docs/media/pr-6-review.mp4`。
- [ ] 把截图和录像发到对话里。停在可合并。等待操作者审阅。

**合并。**

- [ ] 根负责人对确切的头 SHA 给出干净裁决。
- [ ] Bugbot 分流完成。
- [ ] 裁决之后变基到 PR-5，patch-id 不变。
- [ ] 负责人不合并。操作者只在 PR-5 之后落地 PR-6。

## 结束程序

- [ ] 上面每个框都带着证据勾上。
- [ ] 向操作者回复每个 PR 的编号、裁决和头 SHA。不要报告合并。

## 附录 A. 原型证据

分词这一岔已经观察过。草稿仓库是 `/tmp/rag-token-prototype`，分支 `master`，SHA `02ce44c11aac51522a57c4bc63a9d65a19149ccd`。日志是该目录里的 `observed.txt`。检查打印的是得分，所以没有截图。

查询 `一对多怎么映射` 在只按空白分词时，对每份夹具的得分都是 0。同一查询在 ASCII 单词加二字组下，对 `lecture-jpa.pdf` 第 2 页得分是 2，对另外两份是 0。查询 `doGet` 在两种分词下都命中 `lecture-servlet.pdf` 第 1 页。因此 PR-2 两种词都索引。这些句子是为这次检查写的。它们不是课程幻灯片。二字组在真课件上的排序还没有证明。

没有做界面原型。简报已经写了工具窗口或侧边栏，也写了编辑器右键。PR-1 用工具窗口。PR-3 用右键。

下面这些选择留在操作者手里。计划不假装某次运行已经回答了它们。

演示用的实验室或个人 IDE 版本还没有证明。计划钉在 `2026.2.4`，因为 JetBrains 发布记录把这个版本列为构建号 `262.11879.100`，日期是 2026-10-30。更老的实验室镜像会拒绝这个插件。

聊天账号还没有证明。DeepSeek 的 `POST https://api.deepseek.com/chat/completions` 是核对过的请求体。必填字段是 `model` 和 `messages`。文档里的模型 id 是 `deepseek-flash` 和 `deepseek-v4-pro`。同济主机 `https://agi.tongji.edu.cn/` 返回的是登录页，没有 JSON 请求体。PR-3 把基址、模型名和密钥留在设置里。README 示例里的预设是 DeepSeek 地址，直到操作者指定另一个账号。

第三个智能动作还没有证明。若操作者在 PR-6 开始前不替换，PR-6 就做课程提问。另外的形状在附录 B。

真课件还没有证明。测试用夹具。队伍在演示前用 Index files 按钮载入真课件。

## 附录 B. 没有采用的做法

计划采用词表检索。简报要求切块、索引、取出最相关的块，并给出引用。简报没有要求向量。LangChain4j 把全文检索列为一种 RAG。走网络的向量调用会让检索和接口一样慢，演示网络失败时引用就看不到。本地 ONNX 示例用的是英文模型 `bge-small-en-v1.5`，课件可能是中文。这个失败方式是从模型名做出的猜测，没有在真课件上测过。

计划采用自写词表。IntelliJ Community 的 master 捆绑了 `lucene-core` 10.5.1，这份捆绑没有写成插件公开 API。再带一个 Lucene jar，会在两天的演示里和类路径打架。词表够完成引用演示。

聊天采用 JDK 的 HTTP POST。它与核对过的 DeepSeek 请求体一致，少一个库。那个库的示例地址和当前 DeepSeek 页面不一致。只有后续审阅要求幻灯片上出现 `OpenAiChatModel` 这个名字时，才加上它。

提交说明不放在编辑器动作上。输入是项目索引，不是当前打开的文件。动作注册在 `Vcs.MessageActionGroup`，同时在工具窗口放一个按钮，演示时不用去找提交工具栏。

JUnit 文件不立即写入。简报没有要求自动写入。先预览，再点 Insert，讲的人可以在文件改变之前展示场景。

不自动执行 `git commit`。简报要求把说明显示出来。

第三个动作称过三种形状。课程提问针对选区出三个问题，并引用一个块。实验缺口清单会按课件规则检查选区，课件没写这些规则时就做不成。控制台异常解释器是在回答问题，和 F0 重复。默认用课程提问，因为面板显示的是问题，不是一段回答。

本组不做 F2。简报把按指令改代码和内置 diff 查看器分给 C 组。

## 附录 C. 风险

IDE 窗口没有 `control-ui` 或 `control-cli` 技能。每条现场通道仍然写出 `./gradlew test` 和 `./gradlew runIde`。接不上沙箱窗口的通道按失败关闭，不能把一份绿色测试报告当成截图。

`GitChangeUtils.getStagedChanges` 和 `GitLineHandler` 位于 `intellij-community` master 上未审阅的 Git API 清单。PR-5 开始时，负责人对照 `2026.2.4` SDK 检查这些签名。方法缺失时，diff 文本只通过 `GitLineHandler` 跑 `git diff --cached` 取得，空索引也用这条命令的空标准输出来判断。

平台笔记写了 `LightJavaCodeInsightFixtureTestCase5`，它的包名没有核对。PR-1 的测试编译之前，负责人在 `2026.2.4` SDK 里确认这个类。

Tika `4.2.0` 的 PPTX 范围编号跟随幻灯片关系顺序。计划把这个数字称为幻灯片范围。在有人用真课件核对之前，不声称它就是画在幻灯片上的页码。

2026 年 11 月的 Gradle wrapper 补丁号没有核对。PR-1 从 Gradle 发布页读取。

`untilBuild` 为 `262.*` 时，插件不会装进 IDEA `2026.3` 或 `2025.3`。这就是附录 A 里的实验室版本风险。

英文报告 PDF、英文幻灯片 PDF 和 20 分钟演示不是代码 PR。队伍在 PR-6 之后写。报告顺序是队员姓名和学号、功能清单、用户手册、含切块和索引说明的架构节，然后是其他说明。ZIP 里源码放一个文件夹，两份 PDF 放另一个文件夹。Canvas 在 2026-11-03 的 11.00pm 前收到这一个 ZIP。演示时段是 2026-11-04 的 1.30-4.15pm。简报没有写时区。

程序清单要从 `origin/main` 读取 `pstack` 路径。这个仓库里没有这些路径。操作者说开始之后，负责人改从已安装的 poteto-mode 插件读取同样的文件，并记下这次回退。读不到 `control-ui` 是预期情况。

## 附录 D. 链接和阅读清单

动手改代码之前先读这些。

- 用来核对 F0 和 F1 的作业文本，留在写出这份计划的对话旁边。
- [IntelliJ 插件笔记](research/intellij-plugin.md)
- [检索库笔记](research/rag-libraries.md)
- [暂存区 diff 笔记](research/git-staged-diff.md)
- [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Gradle Plugin Portal 上的 org.jetbrains.intellij.platform](https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform)
- [JetBrains 的 IIU 发布记录](https://data.services.jetbrains.com/products/releases?code=IIU&type=release&latest=true)
- [DeepSeek chat completions](https://api-docs.deepseek.com/api/create-chat-completion)
- [Apache Tika download](https://tika.apache.org/download)

PR-2 和 PR-3 的负责人改索引和提问流程之前，先做一次 how 走读。PR-5 的负责人先走读暂存 diff。PR-6 做一次 interrogate，因为第三个动作是队伍自己的设计。运行中的决策记录留在操作者要求的执行笔记里。上面这些选择的记录就是这份计划。

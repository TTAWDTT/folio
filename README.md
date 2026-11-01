# Folio

Folio 是 CST3492 B 组的 IntelliJ 插件。它索引课件，回答时标出页码或幻灯片，并可以起草提交说明、按场景预览 JUnit。中文计划在 `docs/course-ta-plugin-plan.zh.md`。

Folio is an IntelliJ IDEA plugin for CST3492 group B. It indexes lecture PDF and PPTX files, answers with a page or slide citation, drafts three commit messages from the staged diff, and can write a JUnit test after you describe a scenario.

The English plan is `docs/course-ta-plugin-plan.md`. The Chinese plan is `docs/course-ta-plugin-plan.zh.md`.

## Run

Use JDK 25.

```bash
export JAVA_HOME=/path/to/jdk-25
./gradlew test
./gradlew runIde
```

Open the Folio tool window. Save a DeepSeek base URL (`https://api.deepseek.com`), a model name (`deepseek-flash` or `deepseek-v4-pro`), and an API key. Index the lecture files, then ask a question. The citation is shown before the model reply.

The API key is stored with the IDE password safe. It is not written into `folio.xml`.

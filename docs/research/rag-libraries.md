# Retrieval libraries for the course TA plugin

Locked on 1 November 2026 for a demo on 3 November 2026.

The demo parses lecture files with Apache Tika 4.2.0, stores one chunk per PDF page or PPTX slide, and ranks those chunks with an inverted index that lives in the IDE process. The chat call is a DeepSeek chat completion that receives the winning chunks and the question. The citation is the chunk's course-relative path plus its page or slide number, and the plugin shows that citation before the model returns. Embeddings are outside this demo. The Tongji request shape is unverified.

## Overview

F0 is chunk, index, retrieve, send the chunks with the question, and cite the source document and the page or slide. The brief names LangChain4j, Apache Tika, the Tongji AI Services Platform, and DeepSeek. Those names are references. The demo still retrieves when the chat API is slow, so retrieval does not wait on a network call.

The consumer is the student who asks a question during the demo, and the person who grades the citation. The next owner inherits one chunk type, one in-memory index, and one HTTP chat call.

## Key concepts

A chunk is the unit of retrieval and the unit of citation.

- `id` is a string. It is the course-relative path, the scope kind, and the scope number joined together. Re-indexing a file replaces chunks with the same id.
- `sourceDocument` is the course-relative path. It includes the file name. The citation prints this path.
- `scopeKind` is `PAGE` or `SLIDE`.
- `scopeNumber` is an integer. The first page or slide is 1.
- `text` is the extracted text for that page or slide.

A chunk with empty text is not stored. `scopeKind` is one of those two values, and `scopeNumber` is never 0. A second chunk for the same page would be a later change. F0 keeps one chunk per page or slide.

The index is two maps in the plugin process. One map goes from `id` to the chunk. One map goes from a token to a list of chunk ids. A question is tokenized the same way. The score is the count of shared tokens. The plugin keeps the highest scores.

RAG here means the model sees retrieved chunks in the prompt. [LangChain4j's RAG page](https://docs.langchain4j.dev/tutorials/rag) lists full-text search, vector search, and hybrid search as retrieval methods, and then says its own examples are mostly vector search. The written steps are chunk, index, retrieve, prompt, and cite. They leave the retrieval method open. Full-text overlap meets those steps. That reading is a judgment about the brief.

## How it works

1. The plugin reads a course file from disk.
2. Tika parses it to XML with `ToXMLContentHandler`. A body-only handler drops the page and slide markers.
3. PDF chunks are the `div` elements whose class is `page`, in order. PPTX chunks are the `div` elements whose class is `slide-content`, in order. Speaker notes in the following `slide-notes` element stay on that same slide.
4. Each chunk is stored and tokenized into the in-memory index.
5. A question scores the chunks in process. The tool window lists the path and the scope number at once.
6. The plugin then POSTs the question and those chunk texts to DeepSeek. The request asks the model to answer from those chunks and to repeat the path and the scope number. If the POST is slow or fails, the retrieved list is already on screen.

Construct Tika with the plugin class loader. `new AutoDetectParser()` builds `new DefaultParser()`, and that no-arg parser builds Tika's no-arg `ServiceLoader`, which reads the thread context class loader. If that loader is the IDE loader, the PDF and PPTX parsers in the plugin are invisible. `DefaultParser` has a constructor that takes a `ClassLoader`. Pass the plugin class loader. The [IntelliJ class loader page](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html) describes the same context-loader issue for `ServiceLoader`.

For PPTX, set `OfficeParserConfig` `includeSlideMasterContent` to false. The 4.2.0 default is true, so master text is extracted into `slide-master` divs. Index `slide-content` only. Master text would crowd the hits. `includeSlideNotes` defaults to true. Leave notes on, and attach them to the slide they follow.

## Where things live

### Apache Tika

The current release is Apache Tika 4.2.0. The [download page](https://tika.apache.org/download) calls 4.2.0 the latest release and says Tika 4.x requires Java 17 or later. The [Apache release catalog](https://release-catalog.apache.org/tika/index.html) dates 4.2.0 to 28 October 2026. Maven Central `tika-core` metadata lists release 4.2.0, with `lastUpdated` `20261028212016`.

`tika-core` holds the parser API, metadata, `AutoDetectParser`, `ToXMLContentHandler`, and `TikaPagedText`. PDF and PPTX parsers live in the modules below.

`tika-parsers` 4.2.0 is a pom. It aggregates `tika-parsers-standard`, `tika-parsers-extended`, and `tika-parsers-ml`. The packaging is pom, so this artifact supplies no parser classes by itself.

`tika-parsers-standard-package` 4.2.0 is also a pom. It depends on the PDF module, the Microsoft module, and the other standard parser modules.

PDF extraction is `tika-parser-pdf-module` 4.2.0. PPTX extraction is `tika-parser-microsoft-module` 4.2.0. Both jars register a `Parser` service. Depend on `tika-core` plus those two modules. Skip the aggregator poms unless a later file type needs them.

PDF page numbers come from the 4.2.0 `tika-parser-pdf-module` sources. `AbstractPDF2XHTML.startPage` writes a `div` with class `page`. `getCurrentPageNo` returns `pageIndex + 1`. The first page div is page 1. The div has no page-number attribute. `TikaPagedText.PAGE_NUMBER` exists in `tika-core` and is a `reservedInternalInteger`. The PDF parser sets it on a per-page metadata object used while rendering. Count the page divs for the citation.

PPTX slide numbers, from the 4.2.0 `tika-parser-microsoft-module` sources. `SXSLFPowerPointExtractorDecorator.handleSlidePart` writes a `div` with class `slide-content` for each slide relationship. The same class numbers images with `i + 1` while walking `getRelationshipsByType` for the slide relation. The 1-based index of `slide-content` divs is the slide scope to store. A hidden slide still opens that div, then sets hidden metadata, so a hidden slide still consumes a number. This release walks slide relationships. The presentation slide id list is unused in that file. A later copy of the same class on the Tika main branch says relationship order differs from presentation order. I did not parse a sample deck in this session. Treat the number as Tika's slide scope. Whether it matches the number drawn on a given slide is untested.

### IntelliJ Platform and Lucene

The IntelliJ Community master tree bundles Lucene. Fetched on 1 November 2026, [`libraries/lucene.common/intellij.libraries.lucene.common.iml`](https://github.com/JetBrains/intellij-community/blob/master/libraries/lucene.common/intellij.libraries.lucene.common.iml) pins `org.apache.lucene:lucene-core:10.5.1` and `org.apache.lucene:lucene-analysis-common:10.5.1`. The module name is `intellij.libraries.lucene.common`. The [libraries directory](https://github.com/JetBrains/intellij-community/tree/master/libraries) describes these folders as product-embedded library modules for the platform. That pin is the master branch. An installed IDE build can carry a different Lucene version. `lucene-core` 10.5.1 is a real Maven Central artifact.

The plugin SDK does not document the bundled Lucene module as a public API. I did not find a JetBrains page that forbids Lucene by name, so a hard ban is unclear. Two written rules tell a plugin to ship its own library. The [dependencies extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html) says not to use `bundledLibrary()` in production, because direct access to IntelliJ Platform libraries is not recommended. The [class loader page](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html), dated 18 June 2025, says each plugin can ship its own version of a library the IDE also uses.

A plugin may ship `lucene-core` as its own dependency. That is the class loader page's path. The same page gives a Gradle `DEPENDENCY_FIRST` sort so the plugin's jar wins over the IDE's jar on the compile classpath.

The demo ships a small inverted index written in the plugin. It is a token-to-id map plus the chunk map. It stays inside the IDE process. It has no Lucene version to clash with the IDE.

### LangChain4j

Maven Central `dev.langchain4j:langchain4j` release is 1.23.0, with `lastUpdated` `20261030100608`. The [GitHub release](https://github.com/langchain4j/langchain4j/releases) is 1.23.0 on 28 October 2026. `langchain4j-open-ai` 1.23.0 is on Maven Central. The [OpenAI-compatible page](https://docs.langchain4j.dev/integrations/language-models/openai-compatible) shows `OpenAiChatModel.builder()` with `baseUrl`, `apiKey`, and `modelName`, and its sample dependency is `langchain4j-open-ai` 1.23.0.

What LangChain4j adds, from the [RAG tutorial](https://docs.langchain4j.dev/tutorials/rag).

- Document splitters, including `DocumentSplitters.recursive`, paragraph, line, sentence, word, character, and regex splitters. Metadata on the document is copied onto each segment, and each segment gets an `index`.
- Embedding stores, including `InMemoryEmbeddingStore`. Easy RAG embeds with a local ONNX model, `bge-small-en-v1.5`, described there as a 24 megabyte quantized model that runs in process.
- An OpenAI-compatible chat model through `OpenAiChatModel`.

The tutorial also says full-text and hybrid search in LangChain4j are limited to the Azure AI Search and Elasticsearch retrievers. The in-memory Easy RAG path is vector search.

The brief's steps are met without embeddings. LangChain4j's Easy RAG example does require them. A network embedding call at question time would make retrieval as slow as the API, which fails the demo constraint. The local ONNX model would keep retrieval in process. Its name is an English model. I did not measure it on Chinese slides. Guess, from the name only. Chinese lecture text is a poor fit.

For 3 November 2026, leave LangChain4j out of retrieval. A thin HTTP POST can perform the chat call. Add `OpenAiChatModel` only if the grader wants that library on the slide, and keep the index in the plugin. The LangChain4j page's DeepSeek example uses base URL `https://api.deepseek.com/v1` and model name `deepseek-chat`. The DeepSeek API page documents `https://api.deepseek.com` and the model ids `deepseek-flash` and `deepseek-v4-pro`. Follow the DeepSeek page for the URL and the model name. Whether the LangChain4j sample names still work is unverified.

### DeepSeek

DeepSeek documents an OpenAI-compatible chat completions API. The [first-call page](https://api-docs.deepseek.com/) says the API format is compatible with OpenAI, and it sets the OpenAI `base_url` to `https://api.deepseek.com`. The [chat completions page](https://api-docs.deepseek.com/api/create-chat-completion) is `POST /chat/completions`.

The verified request is a POST to `https://api.deepseek.com/chat/completions`. The Authorization header is Bearer plus the API key. The Content-Type header is `application/json`. The body requires `model` and `messages`. `messages` has at least one item. Each item has `role` and `content`. Documented model ids are `deepseek-flash` and `deepseek-v4-pro`. The first-call example reads the answer from `choices[0].message.content`.

The first-call curl also sends `thinking` and `reasoning_effort`. Those fields are in the example. The page I used marks `model` and `messages` as the required body fields. I did not send a live request.

### Tongji

`https://agi.tongji.edu.cn/` is a login page for the Tongji AI service platform. The fetched page has no chat completions URL, no JSON body, and no model id. The request shape on that host is unverified.

The [information office notice of 15 August 2026](https://nic.tongji.edu.cn/info/1126/1575.htm) says a large-model API service exists. It lists API models that were in service on that date, including `DeepSeek-V4-Pro（0813正式版）`, `GLM-5.2`, `DeepSeek-V4-Flash（0731正式版）`, `MiMo-V2.5`, and `Qwen3.8-27B`. It says several older models, including `DeepSeek-R1`, stop on 15 September 2026. The notice has no base URL and no JSON body.

`llmapi.tongji.edu.cn` did not resolve from this environment. I did not confirm a campus-only DNS name. Keep the demo on the verified DeepSeek body until a campus document shows the JSON. Keep the chat base URL in one setting so a later verified campus URL can replace DeepSeek.

## Gotchas

A body-text handler from Tika throws away `page` and `slide-content`. The citation then has a file name and no page or slide. Parse to XML, or count those elements in a content handler.

PPTX scope numbers in Tika 4.2.0 follow slide relationship order. The presentation slide id list is unused in the 4.2.0 extractor. Say "slide" and the index you stored. A match to the number painted on the slide is untested. That check did not happen here.

Master slides are on by default. Turn `includeSlideMasterContent` off before indexing, or the index contains boilerplate from the master.

`new AutoDetectParser()` can see zero parsers inside the IDE. Pass the plugin class loader into `DefaultParser`.

The bundled Lucene module is real on master and is the wrong dependency for this plugin. A hard ban that names Lucene was not found. The production note against `bundledLibrary()` is enough to stay off that jar.

LangChain4j's published DeepSeek example and DeepSeek's own API page disagree on the base URL and the model id. The demo uses the DeepSeek page.

The August 2026 notice lists model names and omits the HTTP body. A handout that still names `DeepSeek-R1` on a campus URL is behind the September 2026 shutdown in that notice.

A whitespace tokenizer fails on Chinese text. Guess about this course. If the slides are Chinese, index character bigrams as well as ASCII words. I did not see the slides.

## Recommendation

Use the in-memory index and DeepSeek. Ship Tika 4.2.0 as `tika-core`, `tika-parser-pdf-module`, and `tika-parser-microsoft-module`. Cite from the chunk record. Show the hits before the chat call.

| Choice | What the demo gets | Failure mode |
| --- | --- | --- |
| In-memory inverted index | Retrieval with the chat API down | Paraphrases score poorly. Whitespace tokens miss Chinese text. The index is gone when the IDE exits unless you save it. |
| Lucene shipped by the plugin | A maintained index library in the plugin class loader | A large API for a two-day demo. The IDE's Lucene can win the compile classpath unless Gradle sorts the plugin jar first. |
| Lucene bundled in the IDE | Lucene 10.5.1 is already in the platform source | No public plugin API documents this use. A hard ban by name was not found. The jar and the version follow the IDE build. |
| LangChain4j Easy RAG, local ONNX | In-process vectors and splitters | The default model is `bge-small-en-v1.5`. Extra native runtime. The deadline is 3 November 2026. |
| Embeddings over the network | Semantic match through an API | Retrieval waits on the network, so a slow API defeats the demo. |
| `OpenAiChatModel` for chat only | The named LangChain4j chat client | Its DeepSeek sample URL and model differ from the current DeepSeek docs. Point both at the DeepSeek page. |
| JDK HTTP POST to DeepSeek | The verified chat contract, one dependency fewer | The answer is empty when DeepSeek is down. The retrieved citations are already visible. |
| Tongji chat API | Campus models, if a later document confirms the body | The request shape is unverified. The August 2026 notice only lists model names. |

## Sources

- [Apache Tika download](https://tika.apache.org/download). Page fetched 1 November 2026. Release 4.2.0.
- [Apache Tika release catalog](https://release-catalog.apache.org/tika/index.html). 4.2.0 released 28 October 2026. Catalog updated 30 October 2026.
- [tika-core Maven metadata](https://repo1.maven.org/maven2/org/apache/tika/tika-core/maven-metadata.xml). Release 4.2.0. lastUpdated 20261028212016.
- [tika-parsers 4.2.0 pom](https://repo1.maven.org/maven2/org/apache/tika/tika-parsers/4.2.0/tika-parsers-4.2.0.pom).
- [tika-parsers-standard-package 4.2.0 pom](https://repo1.maven.org/maven2/org/apache/tika/tika-parsers-standard-package/4.2.0/tika-parsers-standard-package-4.2.0.pom).
- [tika-core 4.2.0 sources](https://repo1.maven.org/maven2/org/apache/tika/tika-core/4.2.0/tika-core-4.2.0-sources.jar).
- [tika-parser-pdf-module 4.2.0 sources](https://repo1.maven.org/maven2/org/apache/tika/tika-parser-pdf-module/4.2.0/tika-parser-pdf-module-4.2.0-sources.jar).
- [tika-parser-microsoft-module 4.2.0 sources](https://repo1.maven.org/maven2/org/apache/tika/tika-parser-microsoft-module/4.2.0/tika-parser-microsoft-module-4.2.0-sources.jar).
- [Tika PPTX extractor on main](https://github.com/apache/tika/blob/main/tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-microsoft-module/src/main/java/org/apache/tika/parser/microsoft/ooxml/SXSLFPowerPointExtractorDecorator.java). Later than 4.2.0. Relationship order versus presentation order.
- [IntelliJ class loaders](https://plugins.jetbrains.com/docs/intellij/plugin-class-loaders.html). 18 June 2025.
- [IntelliJ Platform Gradle dependencies extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html).
- [intellij-community libraries](https://github.com/JetBrains/intellij-community/tree/master/libraries).
- [intellij.libraries.lucene.common.iml](https://github.com/JetBrains/intellij-community/blob/master/libraries/lucene.common/intellij.libraries.lucene.common.iml). Master, fetched 1 November 2026.
- [lucene-core 10.5.1 pom](https://repo1.maven.org/maven2/org/apache/lucene/lucene-core/10.5.1/lucene-core-10.5.1.pom).
- [LangChain4j RAG](https://docs.langchain4j.dev/tutorials/rag).
- [LangChain4j OpenAI-compatible models](https://docs.langchain4j.dev/integrations/language-models/openai-compatible).
- [LangChain4j releases](https://github.com/langchain4j/langchain4j/releases). 1.23.0 on 28 October 2026.
- [langchain4j Maven metadata](https://repo1.maven.org/maven2/dev/langchain4j/langchain4j/maven-metadata.xml). Release 1.23.0. lastUpdated 20261030100608.
- [DeepSeek first API call](https://api-docs.deepseek.com/).
- [DeepSeek chat completions](https://api-docs.deepseek.com/api/create-chat-completion).
- [Tongji AI service platform](https://agi.tongji.edu.cn/).
- [Tongji information office model notice](https://nic.tongji.edu.cn/info/1126/1575.htm). 15 August 2026.

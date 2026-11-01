# Plan for a Java IntelliJ teaching assistant plugin

This plan is for a group B student building an IntelliJ teaching assistant plugin in Java. It locks the build, the platform pin, the extension points, the test entry point, and where settings live. It does not contain implementation code.

Checked on 1 November 2026 against JetBrains docs, the Gradle Plugin Portal, the IntelliJ Platform Plugin Template, and the JetBrains product release feed.

## Overview

Pin IntelliJ Platform Gradle Plugin `org.jetbrains.intellij.platform` version `2.19.0`. Do not use the old plugin id `org.jetbrains.intellij`.

Write the plugin in Java. Put production classes in `src/main/java`. Keep `plugin.xml` in `src/main/resources/META-INF`.

Depend on `intellijIdea("2026.2.4")`. That release is build `262.11879.100`, dated 30 October 2026. Do not pin the 2026.3 release candidate.

Set `sinceBuild` to `262`. Set `untilBuild` to `262.*`. Do not prefix either value with `IU`.

Register the tool window on `com.intellij.toolWindow` with a `ToolWindowFactory`. Register the editor right-click action as an `AnAction` in the `EditorPopupMenu` group. Read the selection from the primary caret offsets. Resolve the caret with `PsiFile.findElementAt` and `PsiTreeUtil.getParentOfType` to a `PsiMethod`, then `PsiMethod.getContainingClass`.

Run tests with the Gradle `test` task. Extend `LightJavaCodeInsightFixtureTestCase5` for Java PSI tests.

Store the API key only in `PasswordSafe`. Store the base URL and the model name in a `PersistentStateComponent`. Do not put the API key in properties.

Compile and test with JDK 25. The Gradle plugin can run on an older runtime, and that older runtime is not enough to compile a 2026.2 plugin.

## Key concepts

The build plugin and the IDE platform are different pins. `org.jetbrains.intellij.platform` 2.19.0 builds the plugin. `intellijIdea("2026.2.4")` is the IDE the plugin compiles against.

`sinceBuild` is the oldest IDE build that may install the plugin. `untilBuild` is the newest. The Gradle plugin writes both into `idea-version` through `patchPluginXml`.

Java is a supported plugin language. The public template still generates Kotlin samples. Java classes live in a Java source set beside those samples.

`AnAction` is still the action class. A tool window is still a `ToolWindowFactory`. Java structure at the caret is still `PsiMethod` and `PsiClass` from the bundled Java plugin `com.intellij.java`.

The API key is a credential. The base URL and the model name are ordinary settings. Those two stores are different APIs on purpose.

## How it works

### Build system

Use IntelliJ Platform Gradle Plugin 2.x. It replaces Gradle IntelliJ Plugin 1.x, which is no longer under active development. Platform 2024.2 and newer requires 2.x. The plugin id is `org.jetbrains.intellij.platform`. The setup page shows version `2.19.0`. The Gradle Plugin Portal lists `2.19.0` as the latest version, created 14 September 2026.

Apply it in the `plugins` block as id `org.jetbrains.intellij.platform` version `2.19.0`.

2.x requires Gradle 9.0.0 or newer and a Java runtime of at least 17 to run Gradle. A known-good Gradle wrapper patch for November 2026 is unverified. Do not invent one.

The old id `org.jetbrains.intellij` is the 1.x plugin. The migration guide says that id changed to `org.jetbrains.intellij.platform`. The 1.x line is obsolete for this assignment. A latest 1.x version number was not verified, so none is listed.

Sources. [Introduction to Plugin Development](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html). [IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html). [Migrating from Gradle IntelliJ Plugin 1.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html). [Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform). [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html). [Incompatible API Changes 2026](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html).

### Java sources

Java is a supported path. The IntelliJ Platform Plugin Template says Java is supported along with Kotlin. Create `src/main/java` and put Java sources there. The generated samples start in `src/main/kotlin`. Replace those samples or leave them unused.

The same README puts `plugin.xml` in `src/main/resources/META-INF`.

The IDE Plugin wizard in IntelliJ IDEA 2026.1 and newer is the other starting point. Its page does not offer a Java or Kotlin choice in the steps it lists. It says the generated project currently targets the platform version compatible with JDK 21. After the wizard, retarget the platform and the JDK as this plan locks them.

Official tutorials still use Java for actions and editor work. `EditorIllustrationAction` and `PopupDialogAction` extend `AnAction` in Java.

`PsiClass` and `PsiMethod` come from the bundled Java plugin. Declare plugin id `com.intellij.java` in `plugin.xml`, and add `bundledPlugin("com.intellij.java")` on the Gradle `intellijPlatform` dependency. The legacy helpers that split Community and Ultimate do not apply to this pin. See the platform section.

Sources. [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/README.md). [Creating a Plugin Project](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html). [Working with Text](https://plugins.jetbrains.com/docs/intellij/working-with-text.html). [Creating Actions](https://plugins.jetbrains.com/docs/intellij/creating-actions-tutorial.html). [IntelliJ IDEA Plugin Development](https://plugins.jetbrains.com/docs/intellij/idea.html). [Dependencies Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html).

### Platform pin and compatibility range

Pin `intellijIdea("2026.2.4")`.

The JetBrains release feed for product code `IIU` lists version `2026.2.4`, build `262.11879.100`, date `2026-10-30`. The same feed for product code `IIC` still lists `2025.3`, build `253.28294.334`, date `2025-12-08`. Current IDEA is one product. `intellijIdeaCommunity` and `intellijIdeaUltimate` remain only for versions earlier than 2025.3.

2026.3 is not the pin. The IDEA Early Access page lists a 2026.3 Release Candidate, build `263.7307.112`, released 29 October 2026. A course plugin should compile against the stable release.

`sinceBuild` and `untilBuild` live under `intellijPlatform.pluginConfiguration.ideaVersion`. They map to `since-build` and `until-build` on `idea-version`. Omit any product code such as `IU`. Marketplace reports `ProductCodePrefixInBuild` when a product code is present.

The branch number is the last two digits of the year plus the release number. By that rule, 2026.1 is `261` and 2026.2 is `262`. The published 2026.2.4 build `262.11879.100` matches `262`. The plugin template states the same minimum build `262` for its 2026.2 target. The Gradle extension docs use `261` only when they mean 2026.1.

The platform version table on the build-number page lists branch `261` for both 2026.2 and 2026.1. That 2026.2 cell disagrees with the branch rule, the release feed, the template, and the extension docs. Do not copy it. `261` for 2026.2 is unverified and contradicted.

`since-build` must not end in `.*`. Use `262`.

The Gradle default for `untilBuild` is the selected platform major plus `.*`, such as `233.*` in the docs. For this pin that default is `262.*`. Set it explicitly so the range is visible.

`262.*` means every 2026.2.x build. It does not include the 2026.3 branch `263`.

The plugin configuration page says it is highly recommended not to set `until-build`, so a Marketplace plugin stays compatible with later IDE builds. The Gradle form for that choice is `untilBuild = provider { null }`. Marketplace also warns `SuspiciousUntilBuild` when `until-build` is not a real build. This assignment is graded against one stable branch while 2026.3 is still a candidate, so the locked range is the closed branch `262.*`. Use the null provider only if the course later publishes the plugin and wants an open end.

`strict-until-build` exists since 2025.3. Use it only when a publisher uploads a new plugin build for each major IDE version. Skip it here.

Build against the oldest platform you claim to support. This plan claims only 2026.2, so build against `2026.2.4`.

Sources. [Dependencies Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html). [JetBrains release feed for IIU and IIC](https://data.services.jetbrains.com/products/releases?code=IIU,IIC&latest=true&type=release). [IntelliJ IDEA Early Access](https://www.jetbrains.com/idea/nextversion/). [Gradle Plugin Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html). [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html). [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html). [Marketplace common errors](https://plugins.jetbrains.com/docs/marketplace/list-of-common-errors-and-warnings.html). [Plugin Template README](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/README.md).

### Tool window, editor action, and PSI

Register the tool window on the extension point `com.intellij.toolWindow`. The required attributes are `id` and `factoryClass`. `factoryClass` implements `ToolWindowFactory`. The platform calls `createToolWindowContent` when the user opens the tool window. `anchor` is `left`, `right`, or `bottom`. For a window that appears only after an action, use `ToolWindowManager.registerToolWindow` instead of the declarative extension.

The editor right-click action is still an `AnAction`. There is no replacement class in the current action docs. Subclass `AnAction`. Override `update` and `actionPerformed`. Override `getActionUpdateThread` when the target is 2022.3 or newer. Return `ActionUpdateThread.BGT`. Background update is the preferred thread, and it is the thread that may read PSI.

Register the action in the `actions` section of `plugin.xml`. Add it to group `EditorPopupMenu`. That group is the editor context menu. The sample element is `add-to-group` with `group-id="EditorPopupMenu"`.

Get the editor from `AnActionEvent.getData(CommonDataKeys.EDITOR)`. A selection exists when `editor.getSelectionModel().hasSelection()` is true. The primary caret is `editor.getCaretModel().getPrimaryCaret()`. The selection offsets are `Caret.getSelectionStart()` and `Caret.getSelectionEnd()`. The document is `editor.getDocument()`. The working-with-text tutorial writes that range with `Document.replaceString`. Read the selected characters from that same range. A method named `getSelectedText` was not on the pages checked for this plan, so that name stays unverified.

Resolve PSI from the caret offset, not from `CommonDataKeys.PSI_ELEMENT`. `PSI_ELEMENT` returns the resolved target when the caret is on a reference, which is a different object from the method that contains the caret.

The offset is `editor.getCaretModel().getOffset()`. One documented path is `PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument())`, then `PsiFile.findElementAt(offset)`. Another documented path is `AnActionEvent.getData(CommonDataKeys.PSI_FILE)`, then `findElementAt`. `findElementAt` returns the leaf at that offset.

Walk up with `PsiTreeUtil.getParentOfType(element, PsiMethod.class)`. The containing class is `PsiMethod.getContainingClass()`. The same `getParentOfType` call takes the type you ask for, so `PsiClass.class` is that call when you want the class directly. Declare the Java plugin dependency before using these types.

Sources. [Tool Windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html). [Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html). [Creating Actions](https://plugins.jetbrains.com/docs/intellij/creating-actions-tutorial.html). [Working with Text](https://plugins.jetbrains.com/docs/intellij/working-with-text.html). [Editor Coordinates](https://plugins.jetbrains.com/docs/intellij/coordinates-system.html). [Editor Components](https://plugins.jetbrains.com/docs/intellij/editor-components.html). [Navigating the PSI](https://plugins.jetbrains.com/docs/intellij/navigating-psi.html). [PSI Elements](https://plugins.jetbrains.com/docs/intellij/psi-elements.html).

### Tests

The light-test entry point for Java PSI is `LightJavaCodeInsightFixtureTestCase5` when the test uses JUnit 5. It has been the JUnit 5 fixture since 2021.1. JUnit 4 uses `LightJavaCodeInsightFixtureTestCase4`. JUnit 3 uses `LightJavaCodeInsightFixtureTestCase`. A new assignment should use the JUnit 5 class. The package name was not printed on the docs page, so it stays unverified.

`BasePlatformTestCase` is the fixture for tests that do not need Java PSI. Do not use it for caret and method tests.

These are light tests. They reuse a project when they can, and they run inside the IDE test sandbox. The Gradle task that runs them is `test`. The IntelliJ Platform Gradle Plugin preconfigures `test` with the platform, the sandbox, and the system properties. `prepareTest` supplies that configuration. `runIde` launches a sandbox IDE for manual checks. It is not the test task. `testIde` is only for an extra test task you register yourself.

Declare test frameworks explicitly. Add `testFramework(TestFrameworkType.Plugin.Java)` for the Java fixture. Add `TestFrameworkType.JUnit5` for the JUnit 5 fixture. The docs example that uses JUnit 4 also shows `junit:junit:4.13.2`. Do not copy that coordinate onto a JUnit 5 test. A verified JUnit 5 library version string was not on the pages used here, so none is locked.

Tests that resolve JDK classes such as `java.lang.String` need a mock JDK from IntelliJ Platform sources. The testing FAQ says to set the `test` task system property `idea.home.path` to a checkout of those sources.

Sources. [Light and Heavy Tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html). [Tests and Fixtures](https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html). [Tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html). [Dependencies Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html). [IntelliJ IDEA Plugin Development](https://plugins.jetbrains.com/docs/intellij/idea.html). [Testing FAQ](https://plugins.jetbrains.com/docs/intellij/testing-faq.html).

### Settings

Put the API key in `PasswordSafe`. The credentials page has an API key section. Build a service name with `generateServiceName`. From Java, call `CredentialAttributesKt.generateServiceName`. Store the key with `PasswordSafe.getInstance().setPassword` and a `CredentialAttributes` that has the service name and no user name. Read it with `getPassword`. Both calls block. Do not call them on the EDT. The Java sample marks the methods `@RequiresBackgroundThread`.

The password is stored by the OS. Windows uses a KeePass file. macOS uses Keychain. Linux uses the Secret Service API. Users can change that under Settings, Appearance and Behavior, System Settings, Passwords.

Do not put the API key in `PropertiesComponent`, in a `PersistentStateComponent` XML file, in `gradle.properties`, or in `plugin.xml`.

Put the model name and the base URL in a `PersistentStateComponent` with a separate state class and `@State` plus `@Storage`. The settings page says a project-level custom file should be preferred for plugin settings. Use a custom storage file. For an application-level component, `other.xml` is deprecated.

`PropertiesComponent` is the properties store. It keeps simple non-roamable values such as toggles and counters. Prefix keys with the plugin id. It is the wrong store for an API key, and it is a weaker store than `PersistentStateComponent` for a base URL and a model name.

The credentials page also lists server URLs as sensitive data. A lab base URL with no token stays in the state component. A base URL that contains a token goes in `PasswordSafe` with the API key.

Sources. [Persisting Sensitive Data](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html). [Persisting State of Components](https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html).

### JDK

A plugin that targets 2026.2 or newer must use Java 25. The incompatible-changes page states that rule. The build-number table lists Java 25 for 2026.2. The plugin template says its 2026.2 target requires Java 25 for compilation and testing, and that the Gradle plugin provisions that toolchain.

The Gradle plugin's own minimum runtime is Java 17. The template says Gradle itself can still run on Java 21, as its CI does. Those numbers are the build-tool floor. They are not the compiler target for 2026.2.

The same template README also tells you to set the project SDK to Java 21. That sentence disagrees with its own 2026.2 paragraph and with the SDK Java table. For this pin, the project compiles with JDK 25.

2026.1 still uses Java 21 and branch `261`. Use that only if a lab image cannot install JDK 25. It is not the November 2026 pin.

Sources. [Incompatible API Changes 2026](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html). [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html). [IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html). [Plugin Template README](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/README.md).

## Where things live

`build.gradle.kts` holds the plugin id `org.jetbrains.intellij.platform`, the `intellijIdea("2026.2.4")` dependency, `bundledPlugin("com.intellij.java")`, `ideaVersion.sinceBuild`, `ideaVersion.untilBuild`, and the test-framework dependencies.

`src/main/java` holds the Java `ToolWindowFactory`, the `AnAction`, and the settings service.

`src/main/resources/META-INF/plugin.xml` holds the tool window extension, the `EditorPopupMenu` action, and the `com.intellij.java` dependency. `patchPluginXml` fills `since-build` and `until-build` from Gradle.

`src/test/java` is the Java test source set that matches `src/main/java`. The template tree shows `src/test/kotlin` because the samples are Kotlin. The README does not print the test directory name for Java, so treat `src/test/java` as the Gradle Java convention applied to that instruction.

The API key lives in the OS credential store through `PasswordSafe`. The base URL and the model name live in the `@Storage` file of the `PersistentStateComponent`.

## Gotchas

The build-number table cell that assigns branch `261` to platform 2026.2 is inconsistent with build `262.11879.100`. Lock `262`.

The wizard text and one template sentence still point at JDK 21. The 2026.2 platform requires JDK 25. Change the generated target after the wizard.

Leaving `untilBuild` at the Gradle default already means `MAJOR.*`. Writing `262.*` makes that choice obvious. Setting `provider { null }` claims later branches, including the 2026.3 candidate, without a test run on those builds.

`CommonDataKeys.PSI_ELEMENT` is the wrong object when the caret sits on a call or a name. Use `findElementAt` and walk to `PsiMethod`.

`PasswordSafe` calls must stay off the EDT. A settings dialog that reads the key on the UI thread will violate that rule.

Light Java tests fail to resolve the fixture classes until `TestFrameworkType.Plugin.Java` is on the test classpath. Tests that need the real JDK classes also need `idea.home.path`.

`runIde` is the manual sandbox. The automated entry point is `test`.

## Sources

- [Introduction to Plugin Development](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)
- [IntelliJ Platform Gradle Plugin 2.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
- [Migrating from Gradle IntelliJ Plugin 1.x](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html)
- [Gradle Plugin Portal for org.jetbrains.intellij.platform](https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform). Version 2.19.0 was created 14 September 2026.
- [Gradle Plugin Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html)
- [Dependencies Extension](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html)
- [Tasks](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html)
- [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html)
- [Plugin Configuration File](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html)
- [Incompatible API Changes 2026](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html)
- [Marketplace common errors](https://plugins.jetbrains.com/docs/marketplace/list-of-common-errors-and-warnings.html)
- [JetBrains release feed](https://data.services.jetbrains.com/products/releases?code=IIU,IIC&latest=true&type=release). IIU 2026.2.4 is dated 30 October 2026. IIC 2025.3 is dated 8 December 2025.
- [IntelliJ IDEA Early Access](https://www.jetbrains.com/idea/nextversion/). The 2026.3 Release Candidate is dated 29 October 2026.
- [IntelliJ Platform Plugin Template README](https://github.com/JetBrains/intellij-platform-plugin-template/blob/main/README.md)
- [Creating a Plugin Project](https://plugins.jetbrains.com/docs/intellij/creating-plugin-project.html)
- [IntelliJ IDEA Plugin Development](https://plugins.jetbrains.com/docs/intellij/idea.html)
- [Tool Windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html)
- [Action System](https://plugins.jetbrains.com/docs/intellij/action-system.html)
- [Creating Actions](https://plugins.jetbrains.com/docs/intellij/creating-actions-tutorial.html)
- [Working with Text](https://plugins.jetbrains.com/docs/intellij/working-with-text.html)
- [Editor Coordinates](https://plugins.jetbrains.com/docs/intellij/coordinates-system.html)
- [Editor Components](https://plugins.jetbrains.com/docs/intellij/editor-components.html)
- [Navigating the PSI](https://plugins.jetbrains.com/docs/intellij/navigating-psi.html)
- [PSI Elements](https://plugins.jetbrains.com/docs/intellij/psi-elements.html)
- [Light and Heavy Tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html)
- [Tests and Fixtures](https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html)
- [Testing FAQ](https://plugins.jetbrains.com/docs/intellij/testing-faq.html)
- [Persisting Sensitive Data](https://plugins.jetbrains.com/docs/intellij/persisting-sensitive-data.html)
- [Persisting State of Components](https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html)

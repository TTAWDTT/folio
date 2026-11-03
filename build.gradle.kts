import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "cst3492"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2.4")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.modules.jcef")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.JUnit5)
    }
    implementation("org.apache.tika:tika-core:4.2.0")
    implementation("org.apache.tika:tika-parser-pdf-module:4.2.0")
    implementation("org.apache.tika:tika-parser-microsoft-module:4.2.0")
    implementation("org.apache.poi:poi-ooxml-full:5.5.1")
    implementation("com.google.code.gson:gson:2.15.0")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    systemProperty("idea.load.plugins.id", "cst3492.folio,com.intellij.java")
    forkEvery = 1
}

val frontendInstall = tasks.register<Exec>("frontendInstall") {
    workingDir("web")
    commandLine("npm", "ci")
    inputs.files("web/package.json", "web/package-lock.json")
    outputs.dir("web/node_modules")
}

val frontendBuild = tasks.register<Exec>("frontendBuild") {
    dependsOn(frontendInstall)
    workingDir("web")
    commandLine("npm", "run", "build")
    inputs.files(fileTree("web") {
        exclude("node_modules/**", "dist/**", "test-results/**", "playwright-report/**")
    }, fileTree("protocol"))
    outputs.dir("web/dist")
}

val bundleFrontend = tasks.register<Sync>("bundleFrontend") {
    dependsOn(frontendBuild)
    from("web/dist")
    into(layout.buildDirectory.dir("generated/frontend/folio-web"))
    doLast {
        val source = file("web/dist")
        val assets = source.walkTopDown().filter { it.isFile }.map { it.relativeTo(source).invariantSeparatorsPath }.sorted().toList()
        require(assets.contains("index.html") && assets.any { it.endsWith(".js") } && assets.any { it.endsWith(".css") }) {
            "Frontend entry, script or stylesheet is missing."
        }
        require(assets.all { it == "index.html" || it.matches(Regex("assets/[a-zA-Z0-9_./-]+")) && !it.contains("..") }) {
            "Frontend contains an unsupported resource path."
        }
        destinationDir.resolve("asset-manifest.json").writeText(assets.joinToString(prefix = "[", postfix = "]") { "\"$it\"" } + "\n")
    }
}

sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("generated/frontend"))
}

tasks.processResources {
    dependsOn(bundleFrontend)
}

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

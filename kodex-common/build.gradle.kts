import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
    id("com.vanniktech.maven.publish") version "0.20.0"
    id("com.github.johnrengelman.shadow")
    id("org.jlleitschuh.gradle.ktlint")
}

group = "nl.jolanrensen.kodex"
version = "0.4.2-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.apache.commons:commons-text:1.10.0")

    shadow("org.jetbrains:markdown-jvm:0.6.1")
    api("org.jgrapht:jgrapht-core:1.5.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1-Beta")

    // logging
    api("io.github.oshai:kotlin-logging:7.0.0")

    // Use JUnit test framework for unit tests
    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:5.5.5")
}

tasks.shadowJar {
    isZip64 = true
    archiveClassifier = ""

    // Avoid clashes with org.jetbrains:markdown-jvm:0.6.1 in :common
    relocate("org.intellij.markdown", "nl.jolanrensen.kodex.markdown")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}

// TODO users should be able to just depend on this module to build extensions

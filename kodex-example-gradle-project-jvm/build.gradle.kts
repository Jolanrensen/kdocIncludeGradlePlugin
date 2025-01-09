import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR_LOG_NOT_FOUND

plugins {
    kotlin("jvm") version "2.0.20"

    // adding the Gradle plugin
    id("nl.jolanrensen.kodex") version "0.4.1-SNAPSHOT"
}

group = "nl.jolanrensen.example"
version = "1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation("org.jetbrains.kotlinx:dataframe:0.15.0")
    testImplementation(kotlin("test"))
}

kodex {
    preprocess(kotlin.sourceSets.main) {
        // optional setup
        arguments(
            ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false,
        )
    }
    preprocess(kotlin.sourceSets.test)
}

tasks.test {
    useJUnitPlatform()
}

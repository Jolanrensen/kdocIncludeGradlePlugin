import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.ARG_DOC_PROCESSOR_LOG_NOT_FOUND
import nl.jolanrensen.kodex.defaultProcessors.COMMENT_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.EXPORT_AS_HTML_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.INCLUDE_FILE_DOC_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.REMOVE_ESCAPE_CHARS_PROCESSOR
import nl.jolanrensen.kodex.defaultProcessors.SAMPLE_DOC_PROCESSOR
import nl.jolanrensen.kodex.gradle.creatingProcessDocTask

plugins {
    kotlin("jvm") version "2.0.20"

    // adding the Gradle plugin
    id("nl.jolanrensen.kodex") version "0.4.0"
}

group = "nl.jolanrensen.example"
version = "1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(11)
}

val koDExSourceSet by kotlin.sourceSets.creating {
    kotlin.srcDir("build/kodex/runKoDEx/src/main/kotlin")
}

tasks.withType<Jar> {
    doFirst {
        println("Jar task: ${this.name}, type: ${this::class.simpleName}")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories

// creates a task to run KoDEx on the main sources named "runKoDEx"
val runKoDEx by tasks.creatingProcessDocTask(sources = kotlinMainSources) {
    task {
        group = "KoDEx"
        description = "Runs KoDEx on the main sources"
    }

    // can be set to customize where the generated files are placed
    target = file("build/kodex/runKoDEx")

    // can be set to customize where the generated html files are placed
    exportAsHtml {
        outputReadOnly = true
        dir = file("build/kodex/runKoDEx/htmlExports")
    }

    // can be set to customize which preprocessors should run and in what order
    processors = listOf(
        INCLUDE_DOC_PROCESSOR,
        INCLUDE_FILE_DOC_PROCESSOR,
        ARG_DOC_PROCESSOR,
        COMMENT_DOC_PROCESSOR,
        SAMPLE_DOC_PROCESSOR,
        EXPORT_AS_HTML_DOC_PROCESSOR,
        REMOVE_ESCAPE_CHARS_PROCESSOR,
    )

    // can be set to customize the arguments passed to individual processors
    arguments += ARG_DOC_PROCESSOR_LOG_NOT_FOUND to false
}

tasks.test {
    useJUnitPlatform()
}

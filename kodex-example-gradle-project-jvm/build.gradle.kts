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

    sourceSets {
        main {}
        // video
        // compose

//        val kodex by creating {
//            kotlin.srcDir("build/kodex/runKoDEx/src/main/kotlin")
//        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:dataframe:0.15.0")
    testImplementation(kotlin("test"))
}

kodex {
    preprocess(kotlin.sourceSets.main) {
        // both true by default if main sourceSet, false if not
        generateJar = true
        generateSourcesJar = true

        // setup details like target directory, processors, etc.
    }
}

//
// val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
//
// // creates a task to run KoDEx on the main sources named "runKodex"
// val runKodex by tasks.creatingRunKodexTask(sources = kotlinMainSources) {
//    group = "KoDEx"
//    description = "Runs KoDEx on the main sources"
//
//    // can be set to customize where the generated files are placed
//    target = file("build/kodex/runKoDEx")
//
//    // can be set to customize where the generated html files are placed
//    exportAsHtml {
//        outputReadOnly = true
//        dir = file("build/kodex/runKoDEx/htmlExports")
//    }
//
//    // can be set to customize which preprocessors should run and in what order
//    processors = listOf(
//        INCLUDE_DOC_PROCESSOR,
//        INCLUDE_FILE_DOC_PROCESSOR,
//        ARG_DOC_PROCESSOR,
//        COMMENT_DOC_PROCESSOR,
//        SAMPLE_DOC_PROCESSOR,
//        EXPORT_AS_HTML_DOC_PROCESSOR,
//        REMOVE_ESCAPE_CHARS_PROCESSOR,
//    )
//
//    // can be set to customize the arguments passed to individual processors
//    arguments.put(ARG_DOC_PROCESSOR_LOG_NOT_FOUND, false)
// }

// tasks.named("compileKotlin").configure {
//    dependsOn(runKodex)
// }

// val kodexCompilation = kotlin.target.compilations.create("kodex") {
//    compileTaskProvider.get().dependsOn(runKodex)
// }

// fun KotlinSourceSet.copyDependenciesTo(other: KotlinSourceSet) {
//    configurations[other.implementationConfigurationName].extendsFrom(configurations[implementationConfigurationName])
//    configurations[other.runtimeOnlyConfigurationName].extendsFrom(configurations[runtimeOnlyConfigurationName])
//    configurations[other.apiConfigurationName].extendsFrom(configurations[apiConfigurationName])
//    configurations[other.runtimeOnlyConfigurationName].extendsFrom(configurations[runtimeOnlyConfigurationName])
// }
//
// kotlin.sourceSets.main.get().copyDependenciesTo(kotlin.sourceSets["kodex"])

tasks.test {
    useJUnitPlatform()
}

// val kodexJar by tasks.creating(Jar::class) {
//    group = "build"
//    description = "Creates a compiled jar file with the KoDEx processed sources"
//    archiveBaseName = project.name
//    archiveVersion = project.version.toString()
//    archiveClassifier = "kodex"
//    from(kodexCompilation.output.allOutputs)
//    dependsOn(kodexCompilation.compileTaskProvider)
// }
//
// val kodexSourcesJar by tasks.creating(Jar::class) {
//    group = "build"
//    description = "Creates a jar file with the KoDEx processed sources"
//    archiveBaseName = project.name
//    archiveVersion = project.version.toString()
//    archiveClassifier = "kodex-sources"
//    from(kotlin.sourceSets["kodex"].kotlin)
//    dependsOn(runKodex)
// }
//
// tasks.assemble {
//    dependsOn(kodexSourcesJar, kodexJar, "kotlinSourcesJar", "jar")
// }

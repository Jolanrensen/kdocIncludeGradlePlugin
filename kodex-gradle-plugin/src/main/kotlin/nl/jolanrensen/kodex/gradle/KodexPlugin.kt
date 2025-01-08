@file:Suppress("unused", "RedundantVisibilityModifier")

package nl.jolanrensen.kodex.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.creating
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.HasKotlinDependencies
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaTarget
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import java.io.File

/**
 * Gradle plugin part of the doc-processor project.
 *
 * Extension functions in this file enable users to more easily create a [RunKodexTask] in their build.gradle.kts
 * file.
 */
class KodexPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit =
        with(project) {
            // add maven central to repositories, which is needed to add dokka as a dependency in RunKodexTasks
            repositories.mavenCentral()

            val objects = objects
            val extension = KodexExtension(objects)
            extensions.add("kodex", extension)

            val kotlinExtension = project.extensions["kotlin"]
                .let { it as? KotlinProjectExtension }
                ?: error("Kotlin extension not found")

            val kotlinSourceSets = kotlinExtension.sourceSets

            afterEvaluate {
                extension.taskCreators.forEach {
                    val inputSourceSet = it.inputSourceSet.get()
                    val sourceSetName = it.sourceSetName.get()

                    val taskName = "preprocess${sourceSetName.replaceFirstChar { it.titlecase() }}"
                    val taskTargetDir = file(project.layout.buildDirectory.dir("kodex/$sourceSetName"))
                    val task = tasks.createRunKodexTask(
                        name = taskName,
                        sources = inputSourceSet.kotlin.sourceDirectories,
                    ) {
                        group = "KoDEx"
                        description = "Runs KoDEx $sourceSetName on the ${inputSourceSet.name} sources"
                        target.set(taskTargetDir)
//                        exportAsHtml {
//                            it.dir = taskTargetDir.dir("html")
//                        }

                        // TODO configure the rest
                    }

                    val kodexSourceSet = kotlinSourceSets.create(sourceSetName) {
                        it.kotlin.setSrcDirs(task.calculateTargets())
                        it.resources.setSrcDirs(inputSourceSet.resources.sourceDirectories)
                        inputSourceSet.copyDependenciesTo(it, project)
                    }

                    val targetsOfInputSourceSet = kotlinExtension.targets.filter { target ->
                        target.compilations.any { compilation ->
                            inputSourceSet in compilation.kotlinSourceSets
                        }
                    }

                    val compilationsOfKodexSourceSet = targetsOfInputSourceSet.associateWith { target ->
                        target.compilations.create(sourceSetName) {
                            it.compileTaskProvider.get().dependsOn(task)
                        }
                    }

                    when (kotlinExtension) {
                        is KotlinMultiplatformExtension -> {
                            TODO()
                            val compilations = kotlinExtension.targets.map {
                                val compilationName =
                                    it.targetName + sourceSetName.replaceFirstChar { it.titlecase() }

                                it.compilations.create(compilationName) {
                                    it.compileTaskProvider.get().dependsOn(task)
                                }
                            }

                            /** [org.jetbrains.kotlin.gradle.plugin.mpp.sourcesJarTask] */
                            for (compilation in compilations) {
                                val jar = tasks.create<Jar>("${compilation.name}Jar") {
                                    group = "build"
                                    description =
                                        "Creates a compiled jar file with the KoDEx processed sources of ${compilation.name}"
                                    archiveBaseName.set(project.name)
                                    archiveVersion.set(project.version.toString())
                                    archiveClassifier.set(compilation.name) // todo
                                    from(compilation.output.allOutputs)
                                    dependsOn(compilation.compileTaskProvider)
                                }
                                tasks.filter {
                                    it != jar && "jar" in it.name.lowercase()
                                }.forEach { it.dependsOn(jar) }
                            }
                        }

                        is KotlinSingleTargetExtension<*> -> {
                            if (it.generateJar.get()) {
                                val target = kotlinExtension.target
                                val compilation = compilationsOfKodexSourceSet[target]
                                    ?: error("Compilation of $target not found")

                                when (target) {
                                    is KotlinWithJavaTarget<*, *>, is KotlinJvmTarget -> {
//                                        compilation as KotlinWithJavaCompilation<*, *>

                                        val jar = tasks.create<Jar>("${compilation.name}Jar") {
                                            group = "build"
                                            description =
                                                "Creates a compiled jar file with the KoDEx processed sources of ${compilation.name}"
                                            archiveBaseName.set(project.name)
                                            archiveVersion.set(project.version.toString())
                                            archiveClassifier.set("kodex")
                                            from(compilation.output.allOutputs)
                                            dependsOn(compilation.compileTaskProvider)
                                        }
                                        tasks.filter {
                                            it != jar && "jar" in it.name.lowercase()
                                        }.forEach { it.dependsOn(jar) }
                                    }

                                    is KotlinJsIrTarget, is KotlinNativeTarget -> TODO("Unsupported target $target")

                                    else -> error("Unsupported target $target")
                                }
                            }

                            if (it.generateSourcesJar.get()) {
                                val sourcesJar = tasks.create<Jar>("${sourceSetName}SourcesJar") {
                                    group = "build"
                                    description =
                                        "Creates a jar file with the KoDEx processed sources of $sourceSetName"
                                    archiveBaseName.set(project.name)
                                    archiveVersion.set(project.version.toString())
                                    archiveClassifier.set("kodex-sources")
                                    from(kodexSourceSet.kotlin)
                                    dependsOn(task)
                                }
                                tasks.filter {
                                    it != sourcesJar && "sourcesjar" in it.name.lowercase()
                                }.forEach { it.dependsOn(sourcesJar) }
                            }
                        }
                    }
                }
            }
        }
}

val KotlinProjectExtension.targets: Iterable<KotlinTarget>
    get() = when (this) {
        is KotlinSingleTargetExtension<*> -> listOf(this.target)
        is KotlinMultiplatformExtension -> targets
        else -> error("Unexpected 'kotlin' extension $this")
    }

fun KotlinSourceSet.copyDependenciesTo(other: KotlinSourceSet, project: Project) {
    val configurations = listOf(
        HasKotlinDependencies::implementationConfigurationName,
        HasKotlinDependencies::runtimeOnlyConfigurationName,
        HasKotlinDependencies::apiConfigurationName,
        HasKotlinDependencies::compileOnlyConfigurationName,
    )

    for (config in configurations) {
        project.configurations[config(other)]
            .extendsFrom(project.configurations[config(this)])
    }
}

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by tasks.creatingProcessDocTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated("Use creatingRunKodexTask instead", ReplaceWith("this.creatingRunKodexTask(sources, block)"))
public fun TaskContainer.creatingProcessDocTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    creating(RunKodexTask::class) {
        this.sources.set(sources)
        block()
    }

/**
 * Create a new [RunKodexTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.creatingProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated("Use createRunKodexTask instead", ReplaceWith("this.createRunKodexTask(name, sources, block)"))
public fun TaskContainer.createProcessDocTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    create<RunKodexTask>(name) {
        this.sources.set(sources)
        block()
    }

/**
 * Create a new [RunKodexTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * tasks.maybeCreateProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated(
    "Use maybeCreateRunKodexTask instead",
    ReplaceWith("this.maybeCreateRunKodexTask(name, sources, block)"),
)
public fun TaskContainer.maybeCreateProcessDocTask(
    name: String,
    sources: Iterable<File>,
    block: RunKodexTask.() -> Unit,
) = maybeCreate(name, RunKodexTask::class.java).apply {
    this.sources.set(sources)
    block()
}

/**
 * Create a new [RunKodexTask] if one with this name doesn't already exist.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * maybeCreateProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 */
@Deprecated(
    "Use maybeCreateRunKodexTask instead",
    ReplaceWith("this.maybeCreateRunKodexTask(name, sources, block)"),
)
public fun Project.maybeCreateProcessDocTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.maybeCreateProcessDocTask(name, sources, block)

/**
 * Create a new [RunKodexTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * creatingProcessDocTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.creatingProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated("Use createRunKodexTask instead", ReplaceWith("this.createRunKodexTask(sources, block)"))
public fun Project.createProcessDocTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.createProcessDocTask(name, sources, block)

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by creatingProcessDocTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createProcessDocTask]
 * @see [TaskContainer.maybeCreateProcessDocTask]
 * @see [TaskContainer.creatingProcessDocTask]
 * @see [Project.createProcessDocTask]
 * @see [Project.maybeCreateProcessDocTask]
 */
@Deprecated("Use creatingRunKodexTask instead", ReplaceWith("this.creatingRunKodexTask(sources, block)"))
public fun Project.creatingProcessDocTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.creatingProcessDocTask(sources, block)

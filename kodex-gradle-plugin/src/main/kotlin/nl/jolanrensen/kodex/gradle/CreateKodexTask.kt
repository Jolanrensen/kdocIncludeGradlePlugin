package nl.jolanrensen.kodex.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.creating
import java.io.File

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by tasks.creatingRunKodexTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun TaskContainer.creatingRunKodexTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
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
 * tasks.creatingRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun TaskContainer.createRunKodexTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
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
 * tasks.maybeCreateRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun TaskContainer.maybeCreateRunKodexTask(
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
 * maybeCreateRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 */
public fun Project.maybeCreateRunKodexTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.maybeCreateRunKodexTask(name, sources, block)

/**
 * Create a new [RunKodexTask].
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * creatingRunKodexTask(name = "processDocs", sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.creatingRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun Project.createRunKodexTask(name: String, sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.createRunKodexTask(name, sources, block)

/**
 * Create a new [RunKodexTask] using by-delegate.
 *
 * For example:
 * ```kotlin
 * val kotlinMainSources = kotlin.sourceSets.main.get().kotlin.sourceDirectories
 * val processDocs by creatingRunKodexTask(sources = kotlinMainSources) {
 *   ...
 * }
 * ```
 *
 * @param [sources] The source directories to process
 *
 * @see [TaskContainer.createRunKodexTask]
 * @see [TaskContainer.maybeCreateRunKodexTask]
 * @see [TaskContainer.creatingRunKodexTask]
 * @see [Project.createRunKodexTask]
 * @see [Project.maybeCreateRunKodexTask]
 */
public fun Project.creatingRunKodexTask(sources: Iterable<File>, block: RunKodexTask.() -> Unit) =
    tasks.creatingRunKodexTask(sources, block)

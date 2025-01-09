package nl.jolanrensen.kodex.gradle

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import javax.inject.Inject

/**
 * The main extension for KoDEx.
 *
 * Here you can attach KoDEx to your Kotlin source sets.
 *
 * ```kt
 * kodex {
 *     preprocess(kotlin.sourceSets["mySourceSet"]) {
 *         generateJar = true
 *         generateSourcesJar = true
 *
 *         processors = listOf(
 *              ...
 *         )
 *         ...
 *     }
 * }
 * ```
 */
abstract class KodexExtension
    @Inject
    constructor(
        private val project: Project,
        private val factory: ObjectFactory,
    ) {
        internal val taskCreators = mutableSetOf<KodexSourceSetTaskCreator>()

        /**
         * Attaches KoDEx to the given [sourceSet].
         *  - Creates a new SourceSet named "${sourceSetName}Kodex" by default ([KodexSourceSetTaskCreator.newSourceSetName]).
         *  - Creates a new task for the given source set named "preprocess${newSourceSetName}" by default ([KodexSourceSetTaskCreator.taskName]).
         *  - This task will preprocess the given source set and store the results at
         *  `build/kodex/$newSourceSetName` by default ([KodexSourceSetTaskCreator.target]).
         *  - Creates a jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskCreator.generateJar]).
         *  - Creates a sources jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskCreator.generateSourcesJar]).
         *
         * @see KodexSourceSetTaskCreator
         */
        public fun preprocess(
            sourceSet: NamedDomainObjectProvider<KotlinSourceSet>,
            block: KodexSourceSetTaskCreator.() -> Unit = {},
        ) = preprocess(sourceSet.get(), block)

        /**
         * Attaches KoDEx to the given [sourceSet].
         *  - Creates a new SourceSet named "${sourceSetName}Kodex" by default ([KodexSourceSetTaskCreator.newSourceSetName]).
         *  - Creates a new task for the given source set named "preprocess${newSourceSetName}" by default ([KodexSourceSetTaskCreator.taskName]).
         *  - This task will preprocess the given source set and store the results at
         *  `build/kodex/$newSourceSetName` by default ([KodexSourceSetTaskCreator.target]).
         *  - Creates a jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskCreator.generateJar]).
         *  - Creates a sources jar task if [sourceSet] is the main source set, and we're not multiplatform
         *  ([KodexSourceSetTaskCreator.generateSourcesJar]).
         *
         * @see KodexSourceSetTaskCreator
         */
        public fun preprocess(sourceSet: KotlinSourceSet, block: KodexSourceSetTaskCreator.() -> Unit = {}) {
            val creator = factory.newInstance(
                KodexSourceSetTaskCreator::class.java,
                sourceSet.name + "Kodex",
            )
            creator.inputSourceSet.set(sourceSet)
            creator.block()
            taskCreators.add(creator)
        }
    }

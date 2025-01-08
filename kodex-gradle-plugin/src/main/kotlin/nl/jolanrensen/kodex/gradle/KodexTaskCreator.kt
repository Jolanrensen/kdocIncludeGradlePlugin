package nl.jolanrensen.kodex.gradle

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.property
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import javax.inject.Inject

abstract class KodexTaskCreator
    @Inject
    constructor(factory: ObjectFactory) {

        @get:Input
        val inputSourceSet: Property<KotlinSourceSet> = factory.property<KotlinSourceSet>()

        @get:Input
        val sourceSetName: Property<String> = factory.property<String>()
            .convention(inputSourceSet.map { it.name + "Kodex" })

        @get:Input
        val generateJar: Property<Boolean> = factory.property<Boolean>()
            .convention(inputSourceSet.map { it.name == "main" })

        @get:Input
        val generateSourcesJar: Property<Boolean> = factory.property<Boolean>()
            .convention(inputSourceSet.map { it.name == "main" })

        // todo
    }

class KodexExtension(private val factory: ObjectFactory) {

    internal val taskCreators = mutableSetOf<KodexTaskCreator>()

    fun preprocess(sourceSet: NamedDomainObjectProvider<KotlinSourceSet>, block: KodexTaskCreator.() -> Unit = {}) =
        preprocess(sourceSet.get(), block)

    fun preprocess(sourceSet: KotlinSourceSet, block: KodexTaskCreator.() -> Unit = {}) {
        val creator = factory.newInstance(KodexTaskCreator::class.java)
        creator.inputSourceSet.set(sourceSet)
        creator.block()
        taskCreators.add(creator)
    }
}

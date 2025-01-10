package nl.jolanrensen.kodex.gradle

import nl.jolanrensen.kodex.RunKodexAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.dokka.DokkaSourceSetImpl
import java.io.File

/**
 * Process docs gradle action.
 *
 * Gradle wrapper for [RunKodexAction].
 */
abstract class RunKodexGradleAction :
    RunKodexAction(),
    WorkAction<RunKodexGradleAction.Parameters> {

    interface Parameters :
        RunKodexAction.Parameters,
        WorkParameters {
        override var baseDir: File
        override var sources: DokkaSourceSetImpl
        override var sourceRoots: List<File>
        override var target: File?
        override var exportAsHtmlDir: File?
        override var processors: List<String>
        override var processLimit: Int
        override var arguments: Map<String, Any?>
        override var outputReadOnly: Boolean
        override var htmlOutputReadOnly: Boolean
    }

    override val parameters: RunKodexAction.Parameters
        get() = getParameters()

    override fun execute() {
        try {
            process()
        } catch (e: Throwable) {
            e.printStackTrace(System.err)
            throw e
        }
    }
}

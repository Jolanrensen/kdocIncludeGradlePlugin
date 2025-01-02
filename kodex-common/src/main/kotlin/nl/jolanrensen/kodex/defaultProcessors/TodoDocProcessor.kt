package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.DocProcessor
import nl.jolanrensen.kodex.DocumentablesByPath
import nl.jolanrensen.kodex.asDocContent
import nl.jolanrensen.kodex.toDocumentablesByPath

/**
 * @see TodoDocProcessor
 */
const val TODO_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.TodoDocProcessor"

/**
 * A doc processor that adds a doc with `TODO`
 * where the docs are missing.
 */
class TodoDocProcessor : DocProcessor() {
    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        documentablesByPath
            .documentablesToProcess
            .map { (path, documentables) ->
                path to documentables.map {
                    if (it.docContent.value.isBlank() || !it.sourceHasDocumentation) {
                        it.copy(
                            docContent = "TODO".asDocContent(),
                            isModified = true,
                        )
                    } else {
                        it
                    }
                }
            }.toDocumentablesByPath()
}

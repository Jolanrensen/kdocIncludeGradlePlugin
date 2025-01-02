package nl.jolanrensen.kodex.defaultProcessors

import nl.jolanrensen.kodex.DocProcessor
import nl.jolanrensen.kodex.DocumentablesByPath
import nl.jolanrensen.kodex.asDocContent
import nl.jolanrensen.kodex.toDocumentablesByPath

/**
 * @see NoDocProcessor
 */
const val NO_DOC_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.NoDocProcessor"

/**
 * A doc processor that simply removes all docs from the sources.
 */
class NoDocProcessor : DocProcessor() {
    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath =
        documentablesByPath
            .documentablesToProcess
            .map { (path, documentables) ->
                path to documentables.map {
                    it.copy(
                        tags = emptySet(),
                        docContent = "".asDocContent(),
                        isModified = true,
                    )
                }
            }.toDocumentablesByPath()
}

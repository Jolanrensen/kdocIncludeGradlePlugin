package nl.jolanrensen.kodex.services

import nl.jolanrensen.kodex.DocAnalyser
import nl.jolanrensen.kodex.DocumentablesByPath
import nl.jolanrensen.kodex.DocumentablesByPathWithCache

class PostIncludeDocProcessorCacheCollector(private val cacheHolder: DocumentablesByPathWithCache) :
    DocAnalyser<Unit>() {

    override fun getAnalyzedResult() = Unit

    override fun analyze(processLimit: Int, documentablesByPath: DocumentablesByPath) {
        documentablesByPath.documentablesToProcess.values.forEach {
            it.forEach { documentable ->
                cacheHolder.updatePostIncludeDocContentResult(documentable)
            }
        }
    }
}

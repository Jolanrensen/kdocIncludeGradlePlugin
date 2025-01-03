package nl.jolanrensen.kodex.defaultProcessors

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.intellij.CompletionInfo
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.processor.DocProcessor
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.utils.getIndicesOfEscapeChars
import nl.jolanrensen.kodex.utils.removeEscapeCharacters

/**
 * @see RemoveEscapeCharsProcessor
 */
const val REMOVE_ESCAPE_CHARS_PROCESSOR = "nl.jolanrensen.kodex.defaultProcessors.RemoveEscapeCharsProcessor"

/**
 * Removes escape characters ('\') from all the docs.
 *
 * Escape characters can also be "escaped" by being repeated.
 * For example, `\\` will be replaced by `\`.
 */
class RemoveEscapeCharsProcessor : DocProcessor() {

    private val escapeChars = listOf('\\')

    override val completionInfos: List<CompletionInfo>
        get() = listOf(
            CompletionInfo(
                tag = "\\",
                blockText = "\\",
                presentableBlockText = "\\X",
                moveCaretOffsetBlock = 0,
                inlineText = "\\",
                presentableInlineText = "\\X",
                moveCaretOffsetInline = 0,
                tailText = "Escape X so it's invisible to other preprocessors. \"\\\" will be removed from the doc.",
            ),
        )

    override fun process(processLimit: Int, documentablesByPath: DocumentablesByPath): DocumentablesByPath {
        val mutableDocs = documentablesByPath
            .toMutable()
            .withDocsToProcessFilter { it.sourceHasDocumentation }

        runBlocking {
            mutableDocs
                .documentablesToProcess
                .flatMap { (_, docs) ->
                    docs.map {
                        launch {
                            it.modifyDocContentAndUpdate(
                                it.docContent.value
                                    .removeEscapeCharacters(escapeChars)
                                    .asDocContent(),
                            )
                        }
                    }
                }.joinAll()
        }

        return mutableDocs
    }

    override fun getHighlightsFor(docContent: DocContent): List<HighlightInfo> =
        buildList {
            docContent.value
                .getIndicesOfEscapeChars(escapeChars)
                .forEach {
                    this += buildHighlightInfo(
                        range = it..it,
                        type = HighlightType.BRACKET,
                        tag = "\\",
                    )
                }
        }
}

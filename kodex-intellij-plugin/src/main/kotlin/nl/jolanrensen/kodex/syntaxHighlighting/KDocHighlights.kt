package nl.jolanrensen.kodex.syntaxHighlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.observable.util.addKeyListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.findParentOfType
import com.intellij.psi.util.startOffset
import nl.jolanrensen.kodex.docContent.asDocTextOrNull
import nl.jolanrensen.kodex.docContent.getDocContentWithMap
import nl.jolanrensen.kodex.getLoadedProcessors
import nl.jolanrensen.kodex.intellij.HighlightInfo
import nl.jolanrensen.kodex.intellij.HighlightType
import nl.jolanrensen.kodex.intellij.applyMapping
import nl.jolanrensen.kodex.kodexHighlightingIsEnabled
import nl.jolanrensen.kodex.processor.DocProcessor
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import java.awt.Font
import java.awt.event.KeyEvent
import java.awt.event.KeyListener

/**
 * This class is responsible for coloring KDoc tags in the editor.
 */
class KDocHighlightAnnotator :
    Annotator,
    DumbAware {

    // are used stateless
    private val loadedProcessors = getLoadedProcessors()

    @Suppress("ktlint:standard:comment-wrapping")
    private fun HighlightInfo.createAsAnnotator(kdoc: KDoc, holder: AnnotationHolder) =
        holder
            .newSilentAnnotation(HighlightSeverity.INFORMATION)
            .let {
                if (description.isNotBlank()) {
                    it.tooltip("$description ($tagProcessorName)")
                } else {
                    it
                }
            }
            .needsUpdateOnTyping()
            .range(
                TextRange(
                    /* startOffset = */ kdoc.startOffset + range.first,
                    /* endOffset = */ kdoc.startOffset + range.last + 1,
                ),
            )
            .enforcedTextAttributes(textAttributesFor(type))
            .create()

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (!kodexHighlightingIsEnabled) return
        if (element !is KDoc) return

        getHighlightInfosFor(element, loadedProcessors).forEach {
            for (it in it) {
                // handled by KDocHighlightListener
                if (it.type == HighlightType.BACKGROUND) continue

                it.createAsAnnotator(element, holder)
            }
        }

        val editor = element.findExistingEditor() ?: return
        KDocHighlightListener.getInstance(editor).updateHighlightingAtCarets()
    }
}

/**
 * This class is responsible for highlighting related symbols such as brackets in KDoc comments and
 * highlighting the background when touching it.
 */
class KDocHighlightListener private constructor(private val editor: Editor) :
    CaretListener,
    KeyListener,
    Disposable {

        companion object {
            private val instanceMap = mutableMapOf<Editor, KDocHighlightListener>()

            fun getInstance(editor: Editor): KDocHighlightListener =
                instanceMap.getOrPut(editor) { KDocHighlightListener(editor) }
        }

        init {
            editor.caretModel.addCaretListener(this, this)
            editor.contentComponent.addKeyListener(this, this)
        }

        private val loadedProcessors = getLoadedProcessors()
        private val highlighters = mutableListOf<RangeHighlighter>()

        override fun caretPositionChanged(event: CaretEvent) = updateHighlightingAtCarets()

        override fun keyTyped(e: KeyEvent?) = updateHighlightingAtCarets()

        override fun keyPressed(e: KeyEvent?) = Unit

        override fun keyReleased(e: KeyEvent?) = updateHighlightingAtCarets()

        /**
         * Updates the highlighting of related symbols such as brackets.
         */
        @Suppress("ktlint:standard:comment-wrapping")
        fun updateHighlightingAtCarets() {
            clearHighlighters()
            if (editor.isDisposed) return dispose()
            if (!kodexHighlightingIsEnabled) return

            val scheme = EditorColorsManager.getInstance().globalScheme
            val markupModel = editor.markupModel as MarkupModelEx
            val psiFile = PsiDocumentManager.getInstance(editor.project ?: return)
                .getPsiFile(editor.document) ?: return

            for (it in editor.caretModel.allCarets) {
                updateHighlightingAtCaret(it, psiFile, scheme, markupModel)
            }
        }

        private fun updateHighlightingAtCaret(
            caret: Caret,
            psiFile: PsiFile,
            scheme: EditorColorsScheme,
            markupModel: MarkupModelEx,
        ) {
            val caretOffset = caret.offset
            val kdoc = psiFile.findElementAt(caretOffset)?.findParentOfType<KDoc>(strict = false) ?: return
            val highlightInfos = getHighlightInfosFor(kdoc, loadedProcessors)
            val kdocStart = kdoc.startOffset

            val relatedHighlightAttributes = scheme.getAttributes(CodeInsightColors.MATCHED_BRACE_ATTRIBUTES)
                .clone()
                .apply {
                    fontType = Font.BOLD + Font.ITALIC
                }

            // background
            val backgroundsToHighlight = highlightInfos
                // take the first group of background highlights, as it's generally the deepest
                .firstOrNull {
                    it.any { caretOffset in (kdocStart + it.range.extendLastByOne()) } &&
                        it.all { it.type == HighlightType.BACKGROUND }
                } ?: emptyList()

            for (it in backgroundsToHighlight) {
                val allToHighlight = (it.related + it).filter { it.type == HighlightType.BACKGROUND }
                for (highlightInfo in allToHighlight) {
                    highlighters += markupModel.addRangeHighlighter(
                        // startOffset =
                        kdocStart + highlightInfo.range.first,
                        // endOffset =
                        kdocStart + highlightInfo.range.last + 1,
                        // layer =
                        HighlighterLayer.ELEMENT_UNDER_CARET - 1,
                        // textAttributes =
                        textAttributesFor(HighlightType.BACKGROUND),
                        // targetArea =
                        HighlighterTargetArea.EXACT_RANGE,
                    )
                }
            }

            // related symbols such as brackets
            val relatedToHighlight = highlightInfos.flatten().firstNotNullOfOrNull {
                // already highlighted related backgrounds
                val related = it.related.filter { it.type != HighlightType.BACKGROUND }
                if (related.isNotEmpty() && caretOffset in (kdocStart + it.range.extendLastByOne())) {
                    if (it.type == HighlightType.BACKGROUND) {
                        related
                    } else {
                        related + it
                    }
                } else {
                    null
                }
            } ?: return

            for (it in relatedToHighlight) {
                highlighters += markupModel.addRangeHighlighter(
                    // startOffset =
                    kdocStart + it.range.first,
                    // endOffset =
                    kdocStart + it.range.last + 1,
                    // layer =
                    HighlighterLayer.SELECTION + 100,
                    // textAttributes =
                    relatedHighlightAttributes,
                    // targetArea =
                    HighlighterTargetArea.EXACT_RANGE,
                )
            }
        }

        private fun clearHighlighters() {
            highlighters.forEach { it.dispose() }
            highlighters.clear()
        }

        override fun dispose() {
            clearHighlighters()
            instanceMap.remove(editor)
        }
    }

private fun textAttributesFor(highlightType: HighlightType): TextAttributes {
    val scheme = EditorColorsManager.getInstance().globalScheme

    val metadataAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.METADATA)
    val kdocLinkAttributes = scheme.getAttributes(KotlinHighlightingColors.KDOC_LINK)
    val commentAttributes = scheme.getAttributes(KotlinHighlightingColors.BLOCK_COMMENT)
    val declarationAttributes = scheme.getAttributes(DefaultLanguageHighlighterColors.CLASS_NAME)

    val backgroundHighlightColor = scheme.getAttributes(KotlinHighlightingColors.SMART_CAST_VALUE)
        .backgroundColor

    return when (highlightType) {
        HighlightType.BRACKET ->
            metadataAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
            }

        HighlightType.TAG ->
            metadataAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = metadataAttributes.foregroundColor
            }

        HighlightType.TAG_KEY ->
            kdocLinkAttributes.clone().apply {}

        HighlightType.TAG_VALUE ->
            declarationAttributes.clone().apply {}

        HighlightType.COMMENT ->
            commentAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
            }

        HighlightType.COMMENT_TAG ->
            commentAttributes.clone().apply {
                fontType = Font.BOLD + Font.ITALIC
                effectType = EffectType.LINE_UNDERSCORE
                effectColor = commentAttributes.foregroundColor
            }

        // handled by KDocHighlightListener, should only be applied when touching
        HighlightType.BACKGROUND -> TextAttributes().apply {
            backgroundColor = backgroundHighlightColor
        }
    }
}

private fun getHighlightInfosFor(kdoc: KDoc, loadedProcessors: List<DocProcessor>): List<List<HighlightInfo>> =
    buildList {
        val docText = kdoc.text.asDocTextOrNull() ?: return@buildList

        // convert the doc text to doc content to retrieve the highlights from the processors
        val (docContent, mapping) = docText.getDocContentWithMap()

        for (processor in loadedProcessors) {
            val highlightInfo = processor.getHighlightsFor(docContent)
                .applyMapping(mapping::get) // map back to doc text indices

            addAll(highlightInfo)
        }
    }

private fun IntRange.extendLastByOne() = first..last + 1

private operator fun Int.plus(range: IntRange) = range.first + this..range.last + this

private operator fun IntRange.plus(int: Int) = this.first + int..this.last + int

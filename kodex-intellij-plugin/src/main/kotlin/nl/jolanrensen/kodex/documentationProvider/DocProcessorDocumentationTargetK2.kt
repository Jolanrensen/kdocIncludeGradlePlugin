@file:Suppress("UnstableApiUsage")

package nl.jolanrensen.kodex.documentationProvider

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.CompositeDocumentationProvider
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import com.intellij.platform.backend.documentation.InlineDocumentationProvider
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiDocCommentBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.processElements
import io.ktor.utils.io.CancellationException
import nl.jolanrensen.kodex.kodexInlineRenderingIsEnabled
import nl.jolanrensen.kodex.services.DocProcessorServiceK2
import nl.jolanrensen.kodex.utils.docComment
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.KotlinDocumentationProvider
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentationProvider
import org.jetbrains.kotlin.idea.kdoc.KDocRenderer.renderKDoc
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.awt.Image
import java.util.function.Consumer

/*
 * K2
 *
 * check out [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationLinkHandler] and
 * [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.resolveKDocLink]
 */

/** by element, used on hover and Ctrl+Q */
class DocProcessorPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {

    init {
        println("DocProcessorPsiDocumentationTargetProvider (K2) created")
    }

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    /**
     * @param element         the element for which the documentation is requested (for example, if the mouse is over
     *                        a method reference, this will be the method to which the reference is resolved).
     * @param originalElement the element under the mouse cursor
     */
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        val service = getService(element.project)
        if (!service.isEnabled) return null
        if (!element.language.`is`(KotlinLanguage.INSTANCE)) return null
        // show documentation based on java presentation
        if (element.navigationElement is KtFile && originalElement?.containingFile is PsiJavaFile) return null

        try {
            val modifiedElement = runBlockingCancellable { service.getModifiedElement(element) } ?: return null
            val kotlinDocTarget = createKotlinDocumentationTarget(
                element = modifiedElement,
                originalElement = originalElement,
            )
            return kotlinDocTarget
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }
}

/**
 * Instantiates the internal
 * [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget] class
 */
private fun createKotlinDocumentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget {
    val kotlinDocumentationTargetClass = Class.forName(
        "org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget",
    )
    val constructor = kotlinDocumentationTargetClass.constructors.first()
    constructor.isAccessible = true
    return constructor.newInstance(element, originalElement) as DocumentationTarget
}

// alternative approach to createKotlinDocumentationTarget which works
// except for clicking links in the documentation
//
// internal class KodexDocumentationTarget(
//    val element: PsiElement,
//    private val originalElement: PsiElement?,
//    private val service: DocProcessorServiceK2,
//    private val pointer: Pointer<KodexDocumentationTarget> = run {
//        val elementPtr = element.createSmartPointer()
//        val originalElementPtr = originalElement?.createSmartPointer()
//        Pointer {
//            KodexDocumentationTarget(
//                element = elementPtr.dereference() ?: return@Pointer null,
//                originalElement = originalElementPtr?.let { it.dereference() ?: return@Pointer null },
//                service = service,
//            )
//        }
//    },
// ) : DocumentationTarget {
//
//    companion object {
//        /**
//         * Reflection-based approach to call into [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.computeLocalDocumentation].
//         */
//        private val computeLocalDocumentation: (
//            element: PsiElement,
//            originalElement: PsiElement?,
//            quickNavigation: Boolean,
//        ) -> String? = run {
//            val klass = Class.forName(
//                "org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTargetKt",
//            )
//            val function = klass.methods.find { "computeLocalDocumentation" in it.name }
//                ?: error("Couldn't find computeLocalDocumentation")
//            function.isAccessible = true
//
//            return@run { element, originalElement, quickNavigation ->
//                function.invoke(null, element, originalElement, quickNavigation) as String?
//            }
//        }
//    }
//
//    private val modifiedElement = service.getModifiedElement(element)!!
//
//    override fun createPointer(): Pointer<out DocumentationTarget> = pointer
//
//    override fun computePresentation(): TargetPresentation = targetPresentation(element)
//
//    override fun computeDocumentationHint(): String? =
//        computeLocalDocumentation(
//            modifiedElement,
// //            element,
//            originalElement,
//            true,
//        )
//
//    override val navigatable: Navigatable?
//        get() = modifiedElement as? Navigatable
//
//    override fun computeDocumentation(): DocumentationResult? {
//        @Suppress("HardCodedStringLiteral")
//        val html = computeLocalDocumentation(
//            modifiedElement,
// //            element,
//            originalElement,
//            false,
//        ) ?: return null
//        return DocumentationResult.documentation(html)
//    }
// }

/**
 * inline, used for rendering single doc comment in file, does not work for multiple, Issue #54,
 * this is handled by [DocProcessorDocumentationProvider].
 *
 * TODO slow, runs a lot!
 */
class DocProcessorInlineDocumentationProvider : InlineDocumentationProvider {

    init {
        println("DocProcessorInlineDocumentationProvider (K2) created")
    }

    class DocProcessorInlineDocumentation(
        private val originalDocumentation: PsiDocCommentBase,
        private val originalOwner: KtDeclaration,
        private val modifiedDocumentation: PsiDocCommentBase,
    ) : InlineDocumentation {

        override fun getDocumentationRange(): TextRange = originalDocumentation.textRange

        override fun getDocumentationOwnerRange(): TextRange? = originalOwner.textRange

        override fun renderText(): String? {
            val docComment = modifiedDocumentation as? KDoc ?: return null
            val result = buildString {
                renderKDoc(
                    contentTag = docComment.getDefaultSection(),
                    sections = docComment.getAllSections(),
                )
            }
            return JavaDocExternalFilter.filterInternalDocInfo(result)
        }

        override fun getOwnerTarget(): DocumentationTarget =
            createKotlinDocumentationTarget(originalOwner, originalOwner)
    }

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    /**
     * Creates [org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinInlineDocumentation]
     */
    val kotlin = KotlinInlineDocumentationProvider()

    // TODO works but is somehow overridden by CompatibilityInlineDocumentationProvider
    // TODO temporarily solved by diverting to DocProcessorDocumentationProvider, Issue #54
    override fun inlineDocumentationItems(file: PsiFile?): Collection<InlineDocumentation> {
        if (file !is KtFile) return emptyList()

        val service = getService(file.project)
        if (!service.isEnabled || !kodexInlineRenderingIsEnabled) return emptyList()

        try {
            val result = mutableListOf<InlineDocumentation>()
            PsiTreeUtil.processElements(file) {
                val owner = it as? KtDeclaration ?: return@processElements true
                val originalDocumentation = owner.docComment as KDoc? ?: return@processElements true
                result += findInlineDocumentation(file, originalDocumentation.textRange) ?: return@processElements true

                true
            }

            return result
        } catch (_: ProcessCanceledException) {
            return emptyList()
        } catch (_: CancellationException) {
            return emptyList()
        } catch (e: Throwable) {
            e.printStackTrace()
            return emptyList()
        }
    }

    override fun findInlineDocumentation(file: PsiFile, textRange: TextRange): InlineDocumentation? {
        val service = getService(file.project)
        if (!service.isEnabled || !kodexInlineRenderingIsEnabled) return null

        try {
            val comment = PsiTreeUtil.getParentOfType(
                file.findElementAt(textRange.startOffset),
                PsiDocCommentBase::class.java,
            ) ?: return null

            if (comment.textRange != textRange) return null

            val declaration = comment.owner as? KtDeclaration ?: return null
            val modified = runBlockingCancellable { service.getModifiedElement(declaration) }

            if (modified == null) return null

            return DocProcessorInlineDocumentation(
                originalDocumentation = declaration.docComment as KDoc,
                originalOwner = declaration,
                modifiedDocumentation = modified.docComment as KDoc,
            )
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: Throwable) {
            e.printStackTrace()
            return null
        }
    }
}

/**
 * k1-like method to render multiple documentation items at once, TODO issue #54
 * Likely to be called often and fail even more, so catching all exceptions.
 */
class DocProcessorDocumentationProvider :
    AbstractDocumentationProvider(),
    ExternalDocumentationProvider {

    init {
        println("DocProcessorDocumentationProvider (K2) created")
    }

    private val kotlin = KotlinDocumentationProvider()

    private val serviceInstances: MutableMap<Project, DocProcessorServiceK2> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorServiceK2.getInstance(project) }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? =
        kotlin.getQuickNavigateInfo(element, originalElement)

    override fun getUrlFor(element: PsiElement?, originalElement: PsiElement?): List<String>? =
        kotlin.getUrlFor(element, originalElement)

    override fun collectDocComments(file: PsiFile, sink: Consumer<in PsiDocCommentBase>) {
        if (file !is KtFile) return
        if (!getService(file.project).isEnabled) return

        try {
            // capture all comments in the file
            processElements(file) {
                val comment = (it as? KtDeclaration)?.docComment
                if (comment != null) {
                    sink.accept(comment)
                }
                true
            }
        } catch (_: ProcessCanceledException) {
        } catch (_: CancellationException) {
        } catch (e: Throwable) {
            // e.printStackTrace()
        }
    }

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?): String? {
        val service = getService(element.project)
        if (!service.isEnabled) return null
        try {
            val modifiedElement = runBlockingCancellable { service.getModifiedElement(element) }
            return kotlin.generateDoc(modifiedElement ?: element, originalElement)
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: Throwable) {
            // e.printStackTrace()
            return null
        }
    }

    override fun generateHoverDoc(element: PsiElement, originalElement: PsiElement?): String? =
        generateDoc(element, originalElement)

    @Nls
    override fun generateRenderedDoc(comment: PsiDocCommentBase): String? {
        val service = getService(comment.project)
        if (!service.isEnabled) return null
        try {
            val owner = comment.owner ?: return null
            val modifiedElement = runBlockingCancellable { service.getModifiedElement(owner) }
            return kotlin.generateRenderedDoc(modifiedElement?.docComment ?: comment)
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: Throwable) {
            // e.printStackTrace()
            return null
        }
    }

    override fun findDocComment(file: PsiFile, range: TextRange): PsiDocCommentBase? =
        kotlin.findDocComment(file, range)

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager,
        `object`: Any?,
        element: PsiElement?,
    ): PsiElement? = kotlin.getDocumentationElementForLookupItem(psiManager, `object`, element)

    override fun getDocumentationElementForLink(
        psiManager: PsiManager,
        link: String,
        context: PsiElement?,
    ): PsiElement? = kotlin.getDocumentationElementForLink(psiManager, link, context)

    @Deprecated("Deprecated in Java")
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
    ): PsiElement? = kotlin.getCustomDocumentationElement(editor, file, contextElement)

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int,
    ): PsiElement? =
        getCustomDocumentationElement(
            editor = editor,
            file = file,
            contextElement = contextElement,
        )

    override fun getLocalImageForElement(element: PsiElement, imageSpec: String): Image? =
        kotlin.getLocalImageForElement(element, imageSpec)

    @Deprecated("Deprecated in Java")
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean =
        CompositeDocumentationProvider.hasUrlsFor(this, element, originalElement)

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean =
        kotlin.canPromptToConfigureDocumentation(element)

    override fun promptToConfigureDocumentation(element: PsiElement?) = kotlin.promptToConfigureDocumentation(element)
}

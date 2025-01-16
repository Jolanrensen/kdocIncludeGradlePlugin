@file:Suppress("UnstableApiUsage")

package nl.jolanrensen.kodex.documentationProvider

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import io.ktor.utils.io.CancellationException
import nl.jolanrensen.kodex.services.DocProcessorService
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile

/** by element, used on hover and Ctrl+Q */
class DocProcessorPsiDocumentationTargetProvider : PsiDocumentationTargetProvider {

    init {
        println("DocProcessorPsiDocumentationTargetProvider (K2) created")
    }

    private val serviceInstances: MutableMap<Project, DocProcessorService> = mutableMapOf()

    private fun getService(project: Project) =
        serviceInstances.getOrPut(project) { DocProcessorService.Companion.getInstance(project) }

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
internal fun createKotlinDocumentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget {
    val kotlinDocumentationTargetClass = Class.forName(
        "org.jetbrains.kotlin.idea.k2.codeinsight.quickDoc.KotlinDocumentationTarget",
    )
    val constructor = kotlinDocumentationTargetClass.constructors.first()
    constructor.isAccessible = true
    return constructor.newInstance(element, originalElement) as DocumentationTarget
}

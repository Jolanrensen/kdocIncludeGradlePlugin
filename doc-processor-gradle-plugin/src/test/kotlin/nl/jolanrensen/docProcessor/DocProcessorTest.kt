package nl.jolanrensen.docProcessor

import com.intellij.openapi.util.TextRange
import nl.jolanrensen.docProcessor.ProgrammingLanguage.KOTLIN
import org.jetbrains.kotlin.resolve.ImportPath
import java.io.File
import java.io.IOException

abstract class DocProcessorTest(name: String) {

    sealed interface Additional

    class AdditionalDocumentable(
        val documentableWrapper: DocumentableWrapper,
    ) : Additional

    class AdditionalDirectory(
        val relativePath: String = "src/main/kotlin/com/example/plugin",
    ) : Additional

    class AdditionalFile(
        val relativePath: String = "src/main/kotlin/com/example/plugin/file.txt",
        val contents: String,
    ) : Additional

    class AdditionalPath(
        val fullyQualifiedPath: String,
    ) : Additional

    fun createDocumentableWrapper(
        documentation: String,
        documentableSourceNoDoc: String,
        fullyQualifiedPath: String,
        docFileTextRange: TextRange,
        fullyQualifiedExtensionPath: String? = null,
        docIndent: Int = 0,
        fileName: String = "Test",
        imports: List<ImportPath> = emptyList(),
        packageName: String = "com.example.plugin",
        language: ProgrammingLanguage = KOTLIN,
    ): DocumentableWrapper = DocumentableWrapper(
        docContent = documentation.getDocContentOrNull()!!,
        programmingLanguage = language,
        imports = imports,
        rawSource = documentation + "\n" + documentableSourceNoDoc,
        fullyQualifiedPath = fullyQualifiedPath,
        fullyQualifiedExtensionPath = fullyQualifiedExtensionPath,
        file = File(
            "src/main/${if (language == KOTLIN) "kotlin" else "java"}/${
                packageName.replace('.', '/')
            }/$fileName.${if (language == KOTLIN) "kt" else "java"}"
        ),
        docFileTextRange = docFileTextRange,
        docIndent = docIndent,
    )

    fun String.textRangeOf(text: String): TextRange = indexOf(text).let { start ->
        TextRange(start, start + text.length)
    }

    @kotlin.jvm.Throws(IOException::class)
    fun processContent(
        documentableWrapper: DocumentableWrapper,
        processors: List<DocProcessor>,
        additionals: List<Additional> = emptyList(),
        processLimit: Int = 10_000,
    ): String {
        val allDocumentables = additionals
            .filterIsInstance<AdditionalDocumentable>()
            .map { it.documentableWrapper } + documentableWrapper

        val additionalPaths = additionals.filterIsInstance<AdditionalPath>()

        if (additionals.any { it is AdditionalDirectory || it is AdditionalFile }) TODO()

        val documentablesPerPath = allDocumentables
            .flatMap { doc ->
                listOfNotNull(doc.fullyQualifiedPath, doc.fullyQualifiedExtensionPath).map { it to doc }
            }
            .groupBy { it.first }
            .mapValues { it.value.map { it.second } }
            .toMutableMap()

        for (path in additionalPaths) {
            if (path.fullyQualifiedPath !in documentablesPerPath) {
                documentablesPerPath[path.fullyQualifiedPath] = emptyList()
            }
        }

        // Run all processors
        val modifiedDocumentables = processors.fold(
            initial = documentablesPerPath as Map<String, List<DocumentableWrapper>>,
        ) { acc, processor ->
            println("Running processor: ${processor::class.qualifiedName}")
            processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
        }

        val originalDoc =
            listOfNotNull(documentableWrapper.fullyQualifiedPath, documentableWrapper.fullyQualifiedExtensionPath)
                .mapNotNull { modifiedDocumentables[it] }
                .flatten()
                .firstOrNull {
                    it.fullyQualifiedPath == documentableWrapper.fullyQualifiedPath
                            && it.fullyQualifiedExtensionPath == documentableWrapper.fullyQualifiedExtensionPath
                            && it.file == documentableWrapper.file
                            && it.docFileTextRange == documentableWrapper.docFileTextRange
                }
                ?: error("Original doc not found for ${documentableWrapper.fullyQualifiedPath} or ${documentableWrapper.fullyQualifiedExtensionPath}")

        return originalDoc.docContent.toDoc(originalDoc.docIndent)
    }
}
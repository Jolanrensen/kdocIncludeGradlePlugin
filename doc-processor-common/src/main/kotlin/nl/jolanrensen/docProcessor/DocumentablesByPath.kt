package nl.jolanrensen.docProcessor

import java.util.*

typealias DocumentableWrapperFilter = (DocumentableWrapper) -> Boolean

interface DocumentablesByPath {

    val queryFilter: DocumentableWrapperFilter?

    val documentablesToProcessFilter: DocumentableWrapperFilter?

    val documentablesToProcess: Map<String, List<DocumentableWrapper>>

    fun query(path: String): List<DocumentableWrapper>

    operator fun invoke(path: String): List<DocumentableWrapper> = query(path)

    fun toMutable(): MutableDocumentablesByPath

    fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath

    fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath

    companion object {
        val EMPTY: DocumentablesByPath = DocumentablesByPathFromMap(emptyMap())

        fun of(map: Map<String, List<DocumentableWrapper>>): DocumentablesByPath = DocumentablesByPathFromMap(map)
        fun of(map: Map<String, List<MutableDocumentableWrapper>>): MutableDocumentablesByPath =
            MutableDocumentablesByPathFromMap(map)
    }
}

fun Map<String, List<DocumentableWrapper>>.toDocumentablesByPath(): DocumentablesByPath = DocumentablesByPath.of(this)
fun Iterable<Pair<String, List<DocumentableWrapper>>>.toDocumentablesByPath(): DocumentablesByPath =
    toMap().toDocumentablesByPath()

interface MutableDocumentablesByPath : DocumentablesByPath {

    override fun query(path: String): List<MutableDocumentableWrapper>

    override operator fun invoke(path: String): List<MutableDocumentableWrapper> = query(path)
    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>>
    override fun toMutable(): MutableDocumentablesByPath = this
}

// region implementations

internal open class DocumentablesByPathFromMap(
    private val allDocs: Map<String, List<DocumentableWrapper>>,
    override val queryFilter: DocumentableWrapperFilter? = null,
    override val documentablesToProcessFilter: DocumentableWrapperFilter? = null,
) : DocumentablesByPath {

    override val documentablesToProcess: Map<String, List<DocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            documentablesToProcessFilter?.let(documentables::filter) ?: documentables
        }.filterValues { it.isNotEmpty() }

    private val docsToQuery: Map<String, List<DocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            queryFilter?.let(documentables::filter) ?: documentables
        }.filterValues { it.isNotEmpty() }

    override fun query(path: String): List<DocumentableWrapper> =
        docsToQuery[path] ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    override fun toMutable(): MutableDocumentablesByPath =
        this as? MutableDocumentablesByPath ?: MutableDocumentablesByPathFromMap(
            allDocs = allDocs.toMutable(),
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}

internal class MutableDocumentablesByPathFromMap(
    private val allDocs: Map<String, List<MutableDocumentableWrapper>>,
    override val queryFilter: DocumentableWrapperFilter? = null,
    override val documentablesToProcessFilter: DocumentableWrapperFilter? = null,
) : MutableDocumentablesByPath {

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            documentablesToProcessFilter?.let(documentables::filter) ?: documentables
        }.filterValues { it.isNotEmpty() }

    private val docsToQuery: Map<String, List<MutableDocumentableWrapper>> = allDocs
        .mapValues { (_, documentables) ->
            queryFilter?.let(documentables::filter) ?: documentables
        }.filterValues { it.isNotEmpty() }

    override fun query(path: String): List<MutableDocumentableWrapper> =
        docsToQuery[path] ?: emptyList()

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathFromMap(
            allDocs = allDocs,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}

internal open class DocumentablesByPathWithCache(
    private val unfilteredDocsToProcess: Map<String, List<DocumentableWrapper>>,
    private val query: (String) -> List<DocumentableWrapper>,
    private val queryAll: () -> Map<String, List<DocumentableWrapper>>,
    override val queryFilter: DocumentableWrapperFilter? = null,
    override val documentablesToProcessFilter: DocumentableWrapperFilter? = null,
) : DocumentablesByPath {

    override val documentablesToProcess: Map<String, List<DocumentableWrapper>> =
        unfilteredDocsToProcess
            .mapValues { (_, documentables) ->
                documentablesToProcessFilter?.let(documentables::filter) ?: documentables
            }.filterValues { it.isNotEmpty() }

    private val queryCache: MutableMap<String, List<DocumentableWrapper>> = mutableMapOf()

    override fun query(path: String): List<DocumentableWrapper> =
        queryCache.getOrPut(path) {
            (unfilteredDocsToProcess[path] ?: query(path))
                .let { values ->
                    queryFilter?.let(values::filter) ?: values
                }
        }

    @Suppress("UNCHECKED_CAST")
    override fun toMutable(): MutableDocumentablesByPath = MutableDocumentablesByPathWithCache(
        unfilteredDocsToProcess = unfilteredDocsToProcess.toMutable(),
        query = { query(it).map { it.toMutable() } },
        queryAll = { queryAll().toMutable() },
        queryFilter = queryFilter,
        documentablesToProcessFilter = documentablesToProcessFilter,
    )

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryAll = queryAll,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): DocumentablesByPath =
        DocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryAll = queryAll,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}

internal class MutableDocumentablesByPathWithCache(
    private val unfilteredDocsToProcess: Map<String, List<MutableDocumentableWrapper>>,
    private val query: (String) -> List<MutableDocumentableWrapper>,
    private val queryAll: () -> Map<String, List<MutableDocumentableWrapper>>,
    override val queryFilter: DocumentableWrapperFilter? = null,
    override val documentablesToProcessFilter: DocumentableWrapperFilter? = null,
) : MutableDocumentablesByPath {

    override val documentablesToProcess: Map<String, List<MutableDocumentableWrapper>> =
        unfilteredDocsToProcess
            .mapValues { (_, documentables) ->
                documentablesToProcessFilter?.let(documentables::filter) ?: documentables
            }.filterValues { it.isNotEmpty() }

    private val queryCache: MutableMap<String, List<MutableDocumentableWrapper>> = mutableMapOf()

    override fun query(path: String): List<MutableDocumentableWrapper> =
        queryCache.getOrPut(path) {
            (unfilteredDocsToProcess[path] ?: query(path))
                .let { values ->
                    queryFilter?.let(values::filter) ?: values
                }
        }

    override fun toMutable(): MutableDocumentablesByPath = this

    override fun withQueryFilter(queryFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryAll = queryAll,
            queryFilter = queryFilter,
            documentablesToProcessFilter = documentablesToProcessFilter,
        )

    override fun withDocsToProcessFilter(docsToProcessFilter: DocumentableWrapperFilter): MutableDocumentablesByPath =
        MutableDocumentablesByPathWithCache(
            unfilteredDocsToProcess = unfilteredDocsToProcess,
            query = query,
            queryAll = queryAll,
            queryFilter = queryFilter,
            documentablesToProcessFilter = docsToProcessFilter,
        )
}
// endregion

/**
 * Converts a [Map]<[String], [List]<[DocumentableWrapper]>> to
 * [Map]<[String], [List]<[MutableDocumentableWrapper]>>.
 *
 * The [MutableDocumentableWrapper] is a copy of the original [DocumentableWrapper].
 */
@Suppress("UNCHECKED_CAST")
private fun Map<String, List<DocumentableWrapper>>.toMutable(): Map<String, List<MutableDocumentableWrapper>> =
    mapValues { (_, documentables) ->
        if (documentables.all { it is MutableDocumentableWrapper }) {
            documentables as List<MutableDocumentableWrapper>
        } else {
            documentables.map { it.toMutable() }
        }
    }
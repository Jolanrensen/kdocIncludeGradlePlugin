package nl.jolanrensen.docProcessor

import nl.jolanrensen.docProcessor.ReferenceState.*
import org.intellij.lang.annotations.Language
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getParentOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.TransparentInlineHolderProvider
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import java.util.*
import kotlin.collections.ArrayDeque

/**
 * Just the contents of the comment, without the `*`-stuff.
 */
typealias DocContent = String

// used to keep track of the current blocks
internal const val CURLY_BRACES = '{'
internal const val SQUARE_BRACKETS = '['
internal const val PARENTHESES = '('
internal const val ANGULAR_BRACKETS = '<'
internal const val BACKTICKS = '`'
internal const val DOUBLE_QUOTES = '"'
internal const val SINGLE_QUOTES = '\''

/**
 * Returns the actual content of the KDoc/Javadoc comment
 */
fun String.getDocContentOrNull(): DocContent? {
    if (isBlank() || !startsWith("/**") || !endsWith("*/")) return null

    val lines = split('\n').withIndex()

    val result = lines.joinToString("\n") { (i, it) ->
        var line = it

        if (i == 0) {
            line = line.trimStart().removePrefix("/**")
        }
        if (i == lines.count() - 1) {
            val lastLine = line.trimStart()

            line = if (lastLine == "*/") {
                ""
            } else {
                lastLine
                    .removePrefix("*")
                    .removeSuffix("*/")
                    .removeSuffix(" ") // optional extra space at the end
            }
        }
        if (i != 0 && i != lines.count() - 1) {
            line = line.trimStart().removePrefix("*")
        }

        line = line.removePrefix(" ") // optional extra space at the start

        line
    }

    return result
}

/**
 * Turns multi-line String into valid KDoc/Javadoc.
 */
fun DocContent.toDoc(indent: Int = 0): String = this
    .split('\n')
    .toMutableList()
    .let {
        it[0] = if (it[0].isEmpty()) "/**" else "/** ${it[0]}"

        val lastIsBlank = it.last().isBlank()

        it[it.lastIndex] = it[it.lastIndex].trim() + " */"

        it.mapIndexed { index, s ->
            buildString {
                if (index != 0) append("\n")
                append(" ".repeat(indent))

                if (!(index == 0 || index == it.lastIndex && lastIsBlank)) {
                    append(" *")
                    if (s.isNotEmpty()) append(" ")
                }
                append(s)
            }
        }.joinToString("")
    }

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 */
fun String.getTagArguments(tag: String, numberOfArguments: Int): List<String> {
    require("@$tag" in this) { "Could not find @$tag in $this" }
    require(numberOfArguments > 0) { "numberOfArguments must be greater than 0" }

    var content = this

    // remove inline tag stuff
    if (content.startsWith("{") && content.endsWith("}")) {
        content = content.removePrefix("{").removeSuffix("}")
    }

    // remove leading spaces
    content = content.trimStart()

    // remove tag
    content = content.removePrefix("@$tag").trimStart()

    val arguments = buildList {
        var currentBlock = ""
        val blocksIndicators = mutableListOf<Char>()

        fun isDone(): Boolean = size >= numberOfArguments - 1
        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        var escapeNext = false
        for (char in content) {
            when {
                escapeNext -> escapeNext = false

                char == '\\' -> escapeNext = true

                isDone() -> Unit

                char.isWhitespace() && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotBlank()) add(currentBlock)
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) when (char) {
                '{' -> blocksIndicators += CURLY_BRACES
                '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                '[' -> blocksIndicators += SQUARE_BRACKETS
                ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)
                '(' -> blocksIndicators += PARENTHESES
                ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)
                '<' -> blocksIndicators += ANGULAR_BRACKETS
                '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)
                '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) blocksIndicators += DOUBLE_QUOTES
                '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) blocksIndicators += SINGLE_QUOTES

                // TODO: issue #11: html tags
            }
            if (isDone() || !(currentBlock.isEmpty() && char.isWhitespace()))
                currentBlock += char
        }

        add(currentBlock)
    }

    val trimmedArguments = arguments.mapIndexed { i, it ->
        when (i) {
            arguments.lastIndex -> // last argument will be kept as is, removing one "splitting" space if it starts with one
                if (it.startsWith(" ") || it.startsWith("\t")) it.drop(1)
                else it

            else -> // other arguments will be trimmed. A newline counts as a space
                it.removePrefix("\n").trimStart(' ', '\t')
        }
    }

    return trimmedArguments
}

/**
 * Can retrieve the arguments of an inline- or block-tag.
 * Arguments are split by spaces, unless they are in a block of "{}", "[]", "()", "<>", "`", """, or "'".
 * Blocks "marks" are ignored if "\" escaped.
 *
 * @param tag The tag name, without the "@", will be removed after removing optional surrounding {}'s
 * @param numberOfArguments The number of arguments to retrieve. This will be the size of the @return list.
 *   The last argument will contain all remaining content, no matter if it can be split or not.
 * @param onRogueClosingChar Optional lambda that will be called when a '}', ']', ')', or '>' is found without respective
 *   opening char. Won't be triggered if '\' escaped.
 * @param isSplitter Defaults to `{ isWhitespace() }`. Can be used to change the splitting behavior.
 */
fun String.getTagArguments(
    tag: String,
    numberOfArguments: Int,
    onRogueClosingChar: (closingChar: Char, argument: Int, indexInArg: Int) -> Unit,
    isSplitter: Char.() -> Boolean,
): List<String> {
    require(numberOfArguments > 0) { "numberOfArguments must be greater than 0" }

    var content = this

    // remove inline tag stuff
    if (content.startsWith("{") && content.endsWith("}")) {
        content = content.removePrefix("{").removeSuffix("}")
    }

    // remove leading spaces
    content = content.trimStart { it.isSplitter() }

    // remove tag
    content = content.removePrefix("@").removePrefix(tag).trimStart { it.isSplitter() }

    val arguments = buildList {
        var currentBlock = ""
        val blocksIndicators = mutableListOf<Char>()

        fun isDone(): Boolean = size >= numberOfArguments - 1
        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        var escapeNext = false
        for (char in content) {
            when {
                escapeNext -> {
                    escapeNext = false
                    continue
                }

                char == '\\' -> escapeNext = true

                isDone() -> Unit

                char.isSplitter() && blocksIndicators.isEmpty() -> {
                    if (!currentBlock.all { it.isSplitter() }) add(currentBlock)
                    currentBlock = ""
                }

                char == '`' -> if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
            }
            if (!isInCodeBlock()) when (char) {
                '{' -> blocksIndicators += CURLY_BRACES
                '}' -> blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                    .let { if (!it) onRogueClosingChar('}', this.size, currentBlock.length) }

                '[' -> blocksIndicators += SQUARE_BRACKETS
                ']' -> blocksIndicators.removeAllElementsFromLast(SQUARE_BRACKETS)
                    .let { if (!it) onRogueClosingChar(']', this.size, currentBlock.length) }

                '(' -> blocksIndicators += PARENTHESES
                ')' -> blocksIndicators.removeAllElementsFromLast(PARENTHESES)
                    .let { if (!it) onRogueClosingChar(')', this.size, currentBlock.length) }

                '<' -> blocksIndicators += ANGULAR_BRACKETS
                '>' -> blocksIndicators.removeAllElementsFromLast(ANGULAR_BRACKETS)
                    .let { if (!it) onRogueClosingChar('>', this.size, currentBlock.length) }

                '"' -> if (!blocksIndicators.removeAllElementsFromLast(DOUBLE_QUOTES)) blocksIndicators += DOUBLE_QUOTES
                '\'' -> if (blocksIndicators.removeAllElementsFromLast(SINGLE_QUOTES)) blocksIndicators += SINGLE_QUOTES

                // TODO: issue #11: html tags
            }
            if (isDone() || !currentBlock.all { it.isSplitter() } || !char.isSplitter())
                currentBlock += char
        }

        add(currentBlock)
    }

    val trimmedArguments = arguments.mapIndexed { i, it ->
        when (i) {
            arguments.lastIndex -> // last argument will be kept as is, removing one "splitting" space if it starts with one
                if (it.first().isSplitter() && it.firstOrNull() != '\n') it.drop(1)
                else it

            else -> // other arguments will be trimmed. A newline counts as a space
                it.removePrefix("\n").trimStart { it.isSplitter() }
        }
    }

    return trimmedArguments
}

/**
 * Decodes something like `[Alias][Foo]` to `Foo`
 * But also `{@link Foo#main(String[])}` to `Foo.main`
 */
fun String.decodeCallableTarget(): String =
    trim()

        .removePrefix("[")
        .removeSuffix("]")

        .let { // for aliased tags like [Foo][Bar]
            if ("][" in it) it.substringAfter("][")
            else it
        }
        .trim()

        .removePrefix("<code>") // for javaDoc
        .removeSuffix("</code>")

        .trim()

        // for javaDoc, attempt to be able to read
        // @include {@link Main#main(String[])} as "Main.main"
        .removePrefix("{") // alternatively for javaDoc
        .removeSuffix("}")
        .removePrefix("@link")
        .trim()
        .replace('#', '.')
        .replace(Regex("""\(.*\)"""), "")
        .trim()

/**
 * Get tag name from the start of some content.
 * Can handle both
 * `  @someTag someContent`
 * and
 * `{@someTag someContent}`
 * and will return "someTag" in these cases.
 */
fun DocContent.getTagNameOrNull(): String? =
    takeIf { it.trimStart().startsWith('@') || it.startsWith("{@") }
        ?.trimStart()
        ?.removePrefix("{")
        ?.removePrefix("@")
        ?.takeWhile { !it.isWhitespace() && it != '{' && it != '}' }

/**
 * Split doc content in blocks of content and text belonging to tags.
 * The tag, if present, can be found with optional (up to max 2) leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes triple backticks and `{@..}` and `${..}` into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitDocContentPerBlock(): List<DocContent> {
    val docContent = this@splitDocContentPerBlock.split('\n')
    return buildList {
        var currentBlock = ""

        /**
         * keeps track of the current blocks
         * denoting `{@..}` with [CURLY_BRACES] and triple "`" with [BACKTICKS]
         */
        val blocksIndicators = mutableListOf<Char>()

        fun isInCodeBlock() = BACKTICKS in blocksIndicators

        for (line in docContent) {

            // start a new block if the line starts with a tag and we're not
            // in a {@..} or ```..``` block
            val lineStartsWithTag = line
                .removePrefix(" ")
                .removePrefix(" ")
                .startsWith("@")

            when {
                // start a new block if the line starts with a tag and we're not in a {@..} or ```..``` block
                lineStartsWithTag && blocksIndicators.isEmpty() -> {
                    if (currentBlock.isNotEmpty())
                        this += currentBlock.removeSuffix("\n")
                    currentBlock = "$line\n"
                }

                line.isEmpty() && blocksIndicators.isEmpty() -> {
                    currentBlock += "\n"
                }

                else -> {
                    if (currentBlock.isEmpty()) {
                        currentBlock = "$line\n"
                    } else {
                        currentBlock += "$line\n"
                    }
                }
            }
            var escapeNext = false
            for ((i, char) in line.withIndex()) {
                when {
                    escapeNext -> {
                        escapeNext = false
                        continue
                    }

                    char == '\\' ->
                        escapeNext = true

                    // ``` detection
                    char == '`' && line.getOrNull(i + 1) == '`' && line.getOrNull(i + 2) == '`' ->
                        if (!blocksIndicators.removeAllElementsFromLast(BACKTICKS)) blocksIndicators += BACKTICKS
                }
                if (isInCodeBlock()) continue
                when {
                    // {@ detection
                    char == '{' && line.getOrNull(i + 1) == '@' ->
                        blocksIndicators += CURLY_BRACES

                    // ${ detection for ArgDocProcessor
                    char == '{' && line.getOrNull(i - 1) == '$' && line.getOrNull(i - 2) != '\\' ->
                        blocksIndicators += CURLY_BRACES

                    char == '}' ->
                        blocksIndicators.removeAllElementsFromLast(CURLY_BRACES)
                }
            }
        }
        add(currentBlock.removeSuffix("\n"))
    }
}

/**
 * Split doc content in blocks of content and text belonging to tags, with the range of the block.
 * The tag, if present, can be found with optional leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes `{}`, `[]`, `()`, and triple backticks into account.
 * Block "marks" are ignored if "\" escaped.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitDocContentPerBlockWithRanges(): List<Pair<DocContent, IntRange>> {
    val splitDocContents = this.splitDocContentPerBlock()
    var i = 0

    return buildList {
        for (docContent in splitDocContents) {
            add(Pair(docContent, i..i + docContent.length))
            i += docContent.length + 1
        }
    }
}

/**
 * Finds all inline tag names, including nested ones,
 * together with their respective range in the doc.
 * The list is sorted by depth, with the deepest tags first and then by order of appearance.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNamesInDocContentWithRanges(): List<Pair<String, IntRange>> {
    val text = this
    val map: SortedMap<Int, MutableList<Pair<String, IntRange>>> = sortedMapOf(Comparator.reverseOrder())

    // holds the current start indices of {@tags found
    val queue = ArrayDeque<Int>()

    var escapeNext = false
    for ((i, char) in this.withIndex()) {
        when {
            escapeNext -> escapeNext = false

            char == '\\' -> escapeNext = true

            char == '{' && this.getOrElse(i + 1) { ' ' } == '@' -> {
                queue.addLast(i)
            }

            char == '}' -> {
                if (queue.isNotEmpty()) {
                    val start = queue.removeLast()
                    val end = i
                    val depth = queue.size
                    val tag = text.substring(start..end)
                    val tagName = tag.getTagNameOrNull()

                    if (tagName != null) {
                        map.getOrPut(depth) { mutableListOf() } += tagName to start..end
                    }
                }
            }
        }
    }
    return map.values.flatten()
}

/**
 * Finds all inline tag names, including nested ones.
 * "{@}" marks are ignored if "\" escaped.
 */
fun DocContent.findInlineTagNamesInDocContent(): List<String> =
    findInlineTagNamesInDocContentWithRanges().map { it.first }

/** Finds all block tag names. */
fun DocContent.findBlockTagNamesInDocContent(): List<String> =
    splitDocContentPerBlock()
        .filter { it.trimStart().startsWith("@") }
        .mapNotNull { it.getTagNameOrNull() }

/** Finds all tag names, including inline and block tags. */
fun DocContent.findTagNamesInDocContent(): List<String> =
    findInlineTagNamesInDocContent() +
            findBlockTagNamesInDocContent()


/** Is able to find an entire JavaDoc/KDoc comment including the starting indent. */
val docRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/""")

val javaLinkRegex = Regex("""\{@link.*}""")

private enum class ReferenceState {
    NONE,
    INSIDE_REFERENCE,
    INSIDE_ALIASED_REFERENCE,
}

/**
 * Replace KDoc links in doc content with the result of [process].
 *
 * Replaces all `[Aliased][ReferenceLinks]` with `[Aliased][ProcessedPath]`
 * and all `[ReferenceLinks]` with `[ReferenceLinks][ProcessedPath]`.
 */
fun DocContent.replaceKdocLinks(process: (String) -> String): DocContent {
    val kdoc = this
    var escapeNext = false
    var insideCodeBlock = false
    var referenceState = NONE

    return buildString {
        var currentBlock = ""

        fun appendCurrentBlock() {
            append(currentBlock)
            currentBlock = ""
        }

        for ((i, char) in kdoc.withIndex()) {
            fun nextChar(): Char? = kdoc.getOrNull(i + 1)
            fun previousChar(): Char? = kdoc.getOrNull(i - 1)

            if (escapeNext) {
                escapeNext = false
            } else {
                when (char) {
                    '\\' -> escapeNext = true

                    '`' -> insideCodeBlock = !insideCodeBlock

                    '[' -> if (!insideCodeBlock) {
                        referenceState =
                            if (previousChar() == ']') {
                                INSIDE_ALIASED_REFERENCE
                            } else {
                                INSIDE_REFERENCE
                            }
                        appendCurrentBlock()
                    }

                    ']' -> if (!insideCodeBlock && nextChar() !in listOf('[', '(')) {
                        currentBlock = processReference(
                            referenceState = referenceState,
                            currentBlock = currentBlock,
                            process = process,
                        )
                        appendCurrentBlock()
                        referenceState = NONE
                    }
                }
            }
            currentBlock += char
        }
        appendCurrentBlock()
    }
}

private fun StringBuilder.processReference(
    referenceState: ReferenceState,
    currentBlock: String,
    process: (String) -> String,
): String {
    var currentReferenceBlock = currentBlock
    when (referenceState) {
        INSIDE_REFERENCE -> {
            val originalRef = currentReferenceBlock.removePrefix("[")
            if (originalRef.startsWith('`') && originalRef.endsWith('`') || ' ' !in originalRef) {
                val processedRef = process(originalRef)
                if (processedRef == originalRef) {
                    append("[$originalRef")
                } else {
                    append("[$originalRef][$processedRef")
                }
                currentReferenceBlock = ""
            }
        }

        INSIDE_ALIASED_REFERENCE -> {
            val originalRef = currentReferenceBlock.removePrefix("[")
            if (originalRef.startsWith('`') && originalRef.endsWith('`') || ' ' !in originalRef) {
                val processedRef = process(originalRef)
                append("[$processedRef")
                currentReferenceBlock = ""
            }
        }

        NONE -> Unit
    }
    return currentReferenceBlock
}

/**
 * Finds and removes the last occurrence of [element] from the list and, if found, all elements after it.
 * Returns true if [element] was found and removed, false otherwise.
 */
fun <T> MutableList<T>.removeAllElementsFromLast(element: T): Boolean {
    val index = lastIndexOf(element)
    if (index == -1) return false
    val indicesToRemove = index..lastIndex
    for (i in indicesToRemove.reversed()) {
        removeAt(i)
    }
    return true
}

fun IntRange.coerceIn(start: Int = Int.MIN_VALUE, endInclusive: Int = Int.MAX_VALUE) =
    first.coerceAtLeast(start)..last.coerceAtMost(endInclusive)

fun DocContent.removeKotlinLinks(): DocContent = buildString {
    val kdoc = this@removeKotlinLinks
    var escapeNext = false
    var insideCodeBlock = false
    var referenceState = NONE

    var currentBlock = ""

    fun appendBlock() {
        append(currentBlock)
        currentBlock = ""
    }

    for ((i, char) in kdoc.withIndex()) {
        fun nextChar(): Char? = kdoc.getOrNull(i + 1)
        fun previousChar(): Char? = kdoc.getOrNull(i - 1)

        when {
            escapeNext -> {
                escapeNext = false
                currentBlock += char
            }

            char == '\\' -> {
                escapeNext = true
            }

            char == '`' -> {
                insideCodeBlock = !insideCodeBlock
                currentBlock += char
            }

            insideCodeBlock -> {
                currentBlock += char
            }

            char == '[' -> {
                referenceState = when {
                    previousChar() == ']' -> {
                        when (referenceState) {
                            INSIDE_REFERENCE -> INSIDE_ALIASED_REFERENCE
                            else -> INSIDE_REFERENCE
                        }
                    }

                    else -> {
                        appendBlock()
                        INSIDE_REFERENCE
                    }
                }
            }

            char == ']' -> {
                if (nextChar() !in listOf('[', '(') || referenceState == INSIDE_ALIASED_REFERENCE) {
                    referenceState = NONE

                    if (currentBlock.startsWith("**") && currentBlock.endsWith("**")) {
                        val trimmed = currentBlock.removeSurrounding("**")
                        currentBlock = "**`$trimmed`**"
                    } else {
                        currentBlock = "`$currentBlock`"
                    }

                    appendBlock()
                }
            }

            referenceState == INSIDE_ALIASED_REFERENCE -> {}

            else -> {
                currentBlock += char
            }
        }
    }
    appendBlock()
}
    .replace("****", "")
    .replace("``", "")


class MyReferenceLinksGeneratingProvider : GeneratingProvider {

    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {

        // reference
        val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_LABEL }
            ?: return

        // optional alias
        val linkTextNode = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }

        val nodeToUse = linkTextNode ?: label

        visitor.consumeTagOpen(node, "code")
        TransparentInlineHolderProvider(1, -1).processNode(visitor, text, nodeToUse)
        visitor.consumeTagClose("code")
    }
}

/**
 * Special version of [org.intellij.markdown.flavours.commonmark.CommonMarkFlavourDescriptor.CodeSpanGeneratingProvider],
 * that will correctly escape table pipes if the code span is inside a table cell.
 */
open class TableAwareCodeSpanGeneratingProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val isInsideTable = isInsideTable(node)
        val nodes = collectContentNodes(node)
        val output = nodes.withIndex().joinToString(separator = "") { (i, it) ->
            if (i == nodes.lastIndex && it.type == MarkdownTokenTypes.ESCAPED_BACKTICKS) {
                // Backslash escapes do not work in code spans.
                // Yet, a code span like `this\` is recognized as "BACKTICK", "TEXT", "ESCAPED_BACKTICKS"
                // So if the last node is ESCAPED_BACKTICKS, we need to manually render it as "\"
                return@joinToString "\\"
            }

            processChild(it, text, isInsideTable).replaceNewLines()
        }.trimForCodeSpan()
        visitor.consumeTagOpen(node, "code")
        visitor.consumeHtml(output)
        visitor.consumeTagClose("code")
    }

    /** From GFM spec: First, line endings are converted to spaces.*/
    protected fun CharSequence.replaceNewLines(): CharSequence =
        replace("\\r\\n?|\\n".toRegex(), " ")

    /**
     * From GFM spec:
     * If the resulting string both begins and ends with a space character,
     * but does not consist entirely of space characters,
     * a single space character is removed from the front and back.
     * This allows you to include code that begins or ends with backtick characters,
     * which must be separated by whitespace from the opening or closing backtick strings.
     */
    protected fun CharSequence.trimForCodeSpan(): CharSequence =
        if (isBlank()) this
        else removeSurrounding(" ", " ")

    protected fun isInsideTable(node: ASTNode): Boolean {
        return node.getParentOfType(GFMTokenTypes.CELL) != null
    }

    protected fun collectContentNodes(node: ASTNode): List<ASTNode> {
        check(node.children.size >= 2)

        // Backslash escapes do not work in code spans.
        // Yet, a code span like `this\` is recognized as "BACKTICK", "TEXT", "ESCAPED_BACKTICKS"
        // Let's keep the last ESCAPED_BACKTICKS and manually render it as "\"
        if (node.children.last().type == MarkdownTokenTypes.ESCAPED_BACKTICKS) {
            return node.children.drop(1)
        }

        return node.children.subList(1, node.children.size - 1)
    }

    protected fun processChild(node: ASTNode, text: String, isInsideTable: Boolean): CharSequence {
        if (!isInsideTable) {
            return HtmlGenerator.leafText(text, node, replaceEscapesAndEntities = false)
        }
        val nodeText = node.getTextInNode(text).toString()
        val escaped = nodeText.replace("\\|", "|")
        return EntityConverter.replaceEntities(escaped, processEntities = false, processEscapes = false)
    }
}

fun DocContent.renderToHtml(theme: Boolean): String {
//TODO https://github.com/JetBrains/markdown
    val flavour = GFMFlavourDescriptor(
        useSafeLinks = false,
        absolutizeAnchorLinks = false,
        makeHttpsAutoLinks = false,
    )
    val md = MarkdownParser(flavour).buildMarkdownTreeFromString(this)
    val linkMap = LinkMap.buildLinkMap(md, this)
    val providers = flavour.createHtmlGeneratingProviders(
        linkMap = linkMap,
        baseURI = null,
    ).toMutableMap()

    val refLinkGenerator = MyReferenceLinksGeneratingProvider()

    // TODO https://github.com/JetBrains/markdown/pull/150
    val codeSpanGenerator = TableAwareCodeSpanGeneratingProvider()

    providers[MarkdownElementTypes.FULL_REFERENCE_LINK] = refLinkGenerator
    providers[MarkdownElementTypes.SHORT_REFERENCE_LINK] = refLinkGenerator
    providers[MarkdownElementTypes.CODE_SPAN] = codeSpanGenerator

    val body = HtmlGenerator(
        markdownText = this,
        root = md,
        providers = providers,
    ).generateHtml()

    return buildString {
        appendLine("<html>")
        if (theme) {
            appendLine("<head>")
            appendLine("<style type=\"text/css\">")
            @Language("css")
            val _1 = appendLine(
                """
                :root {
                    --background: #fff;
                    --background-odd: #f5f5f5;
                    --background-hover: #d9edfd;
                    --header-text-color: #474747;
                    --text-color: #848484;
                    --text-color-dark: #000;
                    --text-color-medium: #737373;
                    --text-color-pale: #b3b3b3;
                    --inner-border-color: #aaa;
                    --bold-border-color: #000;
                    --link-color: #296eaa;
                    --link-color-pale: #296eaa;
                    --link-hover: #1a466c;
                }
                :root[theme="dark"], :root [data-jp-theme-light="false"] {
                    --background: #303030;
                    --background-odd: #3c3c3c;
                    --background-hover: #464646;
                    --header-text-color: #dddddd;
                    --text-color: #b3b3b3;
                    --text-color-dark: #dddddd;
                    --text-color-medium: #b2b2b2;
                    --text-color-pale: #737373;
                    --inner-border-color: #707070;
                    --bold-border-color: #777777;
                    --link-color: #008dc0;
                    --link-color-pale: #97e1fb;
                    --link-hover: #00688e;
                }
                body {
                    font-family: "JetBrains Mono",SFMono-Regular,Consolas,"Liberation Mono",Menlo,Courier,monospace;
                }
                :root {
                    color: #19191C;
                    background-color: #fff;
                }
                :root[theme="dark"] {
                    background-color: #19191C;
                    color: #FFFFFFCC
                }""".trimIndent()
            )
            appendLine("</style>")
            appendLine("</head>")
        }
        appendLine(body)
        appendLine("<html/>")
    }
}

fun IntRange.coerceAtMost(endInclusive: Int) =
    first.coerceAtMost(endInclusive)..last.coerceAtMost(endInclusive)

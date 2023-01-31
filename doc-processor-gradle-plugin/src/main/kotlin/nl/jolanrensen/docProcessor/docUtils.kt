package nl.jolanrensen.docProcessor

import java.util.Comparator

/**
 * Just the contents of the comment, without the `*`-stuff.
 */
typealias DocContent = String

/**
 * Returns the actual content of the KDoc/Javadoc comment
 */
fun String.getDocContent(): DocContent = this
    .split('\n')
    .joinToString("\n") {
        it
            .trimStart()
            .removePrefix("/**")
            .removeSuffix("*/")
            .removePrefix("*")
            .let {
                if (it.startsWith(" ")) it.drop(1)
                else it
            }
    }


/**
 * Turns multi-line String into valid KDoc/Javadoc.
 */
fun DocContent.toDoc(indent: Int = 0): String =
    this
        .split('\n')
        .toMutableList()
        .let {
            it[0] = "/** ${it[0]}".trim()

            val lastIsBlank = it.last().isBlank()

            it[it.lastIndex] = it[it.lastIndex].trim() + " */"

            it.mapIndexed { index, s ->
                buildString {
                    if (index != 0) append("\n")
                    append(" ".repeat(indent))

                    if (!(index == 0 || index == it.lastIndex && lastIsBlank)) {
                        append(" * ")
                    }
                    append(s)
                }
            }.joinToString("")
        }

/**
 * Get @tag target name.
 * For instance, changes `@include [Foo]` to `Foo`
 */
fun DocContent.getTagTarget(tag: String): String =
    also { require("@$tag" in this) { "Could not find @$tag in $this" } }
        .replace("\n", "")
        .trim()
        .removePrefix("{") // for inner tags
        .removeSuffix("}")

        .removePrefix("@$tag")
        .trim()
        .removePrefix("[")
        .removePrefix("[") // twice for scalaDoc
        .removeSuffix("]")
        .removeSuffix("]")

        .let { // for aliased tags like [Foo][Bar]
            if ("][" in it) it.substringAfter("][")
            else it
        }

        .removePrefix("<code>") // for javaDoc
        .removeSuffix("</code>")

        // for javaDoc, attempt to be able to read
        // @include {@link Main#main(String[])} as "Main.main"
        .removePrefix("{") // alternatively for javaDoc
        .removeSuffix("}")
        .trim()
        .removePrefix("@link ")
        .replace('#', '.')
        .replace(Regex("""\(.*\)"""), "")
        .trim()

/**
 * Get file target.
 * For instance, changes `@file (./something.md)` to `./something.md`
 */
fun DocContent.getFileTarget(tag: String): String =
    also { require("@$tag" in this) }
        .replace("\n", "")
        .trim()
        .removePrefix("{") // for inner tags
        .removeSuffix("}")

        .removePrefix("@$tag")
        .trim()

        // TODO figure out how to make file location clickable
        // TODO both for Java and Kotlin
        .removePrefix("(")
        .removeSuffix(")")
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
        ?.takeWhile { !it.isWhitespace() }

fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

/**
 * Expands the receiver String path relative to the current full path.
 * For instance, if the receiver is `plugin.Class.Class2` and the current full path is `com.example.plugin.Class`,
 * the result will be `com.example.plugin.Class.Class2`.
 */
fun String.expandPath(currentFullPath: String): String {
    if (isEmpty() && currentFullPath.isEmpty()) return ""
    if (isEmpty()) return currentFullPath
    if (currentFullPath.isEmpty()) return this

    val targetPath = split(".")
    val parentPath = currentFullPath.split(".")

    var result = ""
    val targetPathIterator = targetPath.iterator()
    val parentPathIterator = parentPath.iterator()

    var nextTarget: String? = targetPathIterator.nextOrNull()
    var nextParent: String? = parentPathIterator.nextOrNull()

    while (nextTarget != null || nextParent != null) {
        if (nextTarget == nextParent) {
            result += "$nextParent."
            nextTarget = targetPathIterator.nextOrNull()
            nextParent = parentPathIterator.nextOrNull()
        } else if (nextParent != null) {
            result += "$nextParent."
            nextParent = parentPathIterator.nextOrNull()
        } else {
            result += "$nextTarget."
            nextTarget = targetPathIterator.nextOrNull()
        }
    }

    return result.dropLastWhile { it == '.' }
}

/**
 * Split doc content in blocks of content and text belonging to tags.
 * The tag, if present, can be found with optional leading spaces in the first line of the block.
 * You can get the name with [String.getTagNameOrNull].
 * Splitting takes `{}`, `[]`, `()`, and triple backticks into account.
 * Can be joint with '\n' to get the original content.
 */
fun DocContent.splitDocContentPerBlock(): List<DocContent> = buildList {
    val docContent = this@splitDocContentPerBlock.split('\n')

    var currentBlock = ""
    val blocksIndicators = mutableListOf<String>()
    for (line in docContent) {

        // start a new block if the line starts with a tag and we're not
        // in a {} or `````` block
        if (line.trimStart().startsWith("@") && blocksIndicators.isEmpty()) {
            add(currentBlock)
            currentBlock = line
        } else {
            if (currentBlock.isEmpty()) {
                currentBlock = line
            } else {
                currentBlock += "\n$line"
            }
        }
        for (char in line) when (char) {
            '{' -> blocksIndicators += "{}"
            '}' -> blocksIndicators -= "{}"

            '[' -> blocksIndicators += "[]"
            ']' -> blocksIndicators -= "[]"

            '(' -> blocksIndicators += "()"
            ')' -> blocksIndicators -= "()"
        }
        val numberOfBackTicks = line.count { it == '`' } / 3
        repeat(numberOfBackTicks) {
            if ("```" in blocksIndicators)
                blocksIndicators -= "```"
            else
                blocksIndicators += "```"
        }
    }
    add(currentBlock)
}

/** Finds any inline tag with its depth, preferring the innermost one.*/
private fun DocContent.findInlineTagRangeWithDepthOrNull(): Pair<IntRange, Int>? {
    var depth = 0
    var start: Int? = null
    for ((i, char) in this.withIndex()) {
        if (char == '{' && this.getOrNull(i + 1) == '@') {
            start = i
            depth++
        } else if (char == '}') {
            if (start != null) {
                return Pair(start..i, depth)
            }
        }
    }
    return null
}

/**
 * Finds all inline tag names, including nested ones,
 * together with their respective range in the doc.
 * The list is sorted by depth, with the deepest tags first and then by order of appearance.
 */
fun DocContent.findInlineTagNamesInDocContentWithRanges(): List<Pair<String, IntRange>> {
    var text = this

    return buildMap<Int, MutableList<Pair<String, IntRange>>> {
        while (text.findInlineTagRangeWithDepthOrNull() != null) {
            val (range, depth) = text.findInlineTagRangeWithDepthOrNull()!!
            val comment = text.substring(range)
            comment.getTagNameOrNull()?.let { tagName ->
                getOrPut(depth) { mutableListOf() } += Pair(tagName, range)
            }

            text = text.replaceRange(
                range = range,
                replacement = comment
                    .replace('{', '<')
                    .replace('}', '>'),
            )
        }
    }.toSortedMap(Comparator.reverseOrder())
        .flatMap { it.value }
}

/** Finds all inline tag names, including nested ones. */
fun DocContent.findInlineTagNamesInDocContent(): List<String> =
    findInlineTagNamesInDocContentWithRanges().map { it.first }

fun DocContent.findBlockTagNamesInDocContent(): List<String> =
    splitDocContentPerBlock()
        .filter { it.trimStart().startsWith("@") }
        .mapNotNull { it.getTagNameOrNull() }

fun DocContent.findTagNamesInDocContent(): List<String> =
    findInlineTagNamesInDocContent() + findBlockTagNamesInDocContent()


/**
 * Is able to find an entire JavaDoc/KDoc comment including the starting indent.
 */
val docRegex = Regex("""( *)/\*\*([^*]|\*(?!/))*?\*/""")

/**
 * Is able to find JavaDoc/KDoc tags without content.
 * Tags can be at the beginning of a line or after a `{`, like in `{@see String}`
 */
val tagRegex = Regex("""(^|\n|\{) *@[a-zA-Z][a-zA-Z0-9]*""")
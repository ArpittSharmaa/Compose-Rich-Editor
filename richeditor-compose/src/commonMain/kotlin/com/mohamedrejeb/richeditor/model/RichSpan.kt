package com.mohamedrejeb.richeditor.model

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.utils.customMerge
import com.mohamedrejeb.richeditor.utils.isSpecifiedFieldsEquals

/**
 * A rich span is a part of a rich paragraph.
 */
public class RichSpan(
    internal val key: Int? = null,
    val children: SnapshotStateList<RichSpan> = mutableStateListOf(),
    var paragraph: RichParagraph,
    var parent: RichSpan? = null,
    var text: String = "",
    var textRange: TextRange = TextRange(start = 0, end = 0),
    var spanStyle: SpanStyle = SpanStyle(),
) {
    /**
     * Return the full text range of the rich span.
     * It merges the text range of the rich span with the text range of its children.
     *
     * @return The full text range of the rich span
     */
    private val fullTextRange: TextRange get() {
        var textRange = this.textRange
        var lastChild: RichSpan? = this
        while (true) {
            lastChild = lastChild?.children?.lastOrNull()
            if (lastChild == null) break
            textRange = TextRange(
                start = textRange.min,
                end = lastChild.textRange.max
            )
        }
        return textRange
    }

    /**
     * Return the full span style of the rich span.
     * It merges the span style of the rich span with the span style of its parents.
     *
     * @return The full span style of the rich span
     */
    val fullSpanStyle: SpanStyle get() {
        var spanStyle = this.spanStyle
        var parent = this.parent

        while (parent != null) {
            spanStyle = parent.spanStyle.merge(spanStyle)
            parent = parent.parent
        }

        return spanStyle
    }

    /**
     * Check if the rich span is the first in the paragraph
     *
     * @return True if the rich span is the first in the paragraph, false otherwise
     */
    val isFirstInParagraph: Boolean get() {
        var current: RichSpan
        var parent: RichSpan = this

        while (true) {
            current = parent
            parent = current.parent ?: break

            if (parent.children.firstOrNull() != current || parent.text.isNotEmpty()) return false
        }

        return paragraph.children.firstOrNull() == current
    }

    /**
     * Check if the rich span is the last in the paragraph
     *
     * @return True if the rich span is the last in the paragraph, false otherwise
     */
    val isLastInParagraph: Boolean get() {
        var current: RichSpan
        var parent: RichSpan = this

        if (!isChildrenEmpty()) return false

        while (true) {
            current = parent
            parent = current.parent ?: break

            if (parent.children.lastOrNull() != current) return false
        }

        return paragraph.children.lastOrNull() == current
    }

    /**
     * Check if the rich span is empty.
     * A rich span is empty if its text is empty and its children are empty
     *
     * @return True if the rich span is empty, false otherwise
     */
    fun isEmpty(): Boolean = text.isEmpty() && isChildrenEmpty()

    /**
     * Check if the rich span children are empty
     *
     * @return True if the rich span children are empty, false otherwise
     */
    private fun isChildrenEmpty(): Boolean =
        children.all { richSpan ->
            richSpan.text.isEmpty() && richSpan.isChildrenEmpty()
        }

    /**
     * Get the first non-empty child
     *
     * @return The first non-empty child or null if there is no non-empty child
     */
    internal fun getFirstNonEmptyChild(): RichSpan? {
        children.forEach { richSpan ->
            if (richSpan.text.isNotEmpty()) return richSpan
            else {
                val result = richSpan.getFirstNonEmptyChild()
                if (result != null) return result
            }
        }
        return null
    }

    /**
     * Get the rich span by text index
     *
     * @param textIndex The text index to search
     * @param offset The offset of the text range
     * @return A pair of the offset and the rich span or null if the rich span is not found
     */
    fun getRichSpanByTextIndex(
        textIndex: Int,
        offset: Int = 0,
    ): Pair<Int, RichSpan?> {
        var index = offset

        // Set start text range
        textRange = TextRange(start = index, end = index + text.length)
        index += this.text.length

        // Check if the text index is in the start text range
        if (
            (textIndex in textRange || (isFirstInParagraph && textIndex + 1 == textRange.min))
        ) {
            return if (text.isEmpty()) {
                index to paragraph.getFirstNonEmptyChild()
            } else {
                index to this
            }
        }

        // Check if the text index is in the children
        children.forEach { richSpan ->
            val result = richSpan.getRichSpanByTextIndex(
                textIndex = textIndex,
                offset = index,
            )
            if (result.second != null)
                return result
            else
                index = result.first
        }

        return index to null
    }

    /**
     * Get the rich span list by text range
     *
     * @param searchTextRange The text range to search
     * @param offset The offset of the text range
     * @return The rich span list
     */
    fun getRichSpanListByTextRange(
        searchTextRange: TextRange,
        offset: Int = 0,
    ): Pair<Int, List<RichSpan>> {
        var index = offset
        val richSpanList = mutableListOf<RichSpan>()

        // Set start text range
        textRange = TextRange(start = index, end = index + text.length)
        index += this.text.length

        // Check if the text index is in the start text range
        if (searchTextRange.min < textRange.max && searchTextRange.max > textRange.min)
            richSpanList.add(this)

        // Check if the text index is in the children
        children.forEach { richSpan ->
            val result = richSpan.getRichSpanListByTextRange(
                searchTextRange = searchTextRange,
                offset = index,
            )
            richSpanList.addAll(result.second)
            index = result.first
        }

        return index to richSpanList
    }

    /**
     * Remove text range from the rich span
     *
     * @param textRange The text range to remove
     * @return The rich span with the removed text range or null if the rich span is empty
     */
    fun removeTextRange(textRange: TextRange): RichSpan? {
        // Remove all text if it's in the text range
        if (textRange.min <= this.textRange.min && textRange.max >= this.textRange.max) {
            this.textRange = TextRange(start = 0, end = 0)
            text = ""
        }
        // Remove text from start and end
        else if (textRange.min in this.textRange || (textRange.max - 1) in this.textRange) {
            val startFirstHalf = 0 until (textRange.min - this.textRange.min)
            val startSecondHalf = (textRange.max - this.textRange.min) until (this.textRange.max - this.textRange.min)
            val newStartText =
                (if (startFirstHalf.isEmpty()) "" else text.substring(startFirstHalf)) +
                (if (startSecondHalf.isEmpty()) "" else text.substring(startSecondHalf))

            this.textRange = TextRange(start = this.textRange.min, end = this.textRange.min + newStartText.length)
            text = newStartText
        }

        // Remove text from children
        for (i in children.lastIndex downTo 0) {
            val richSpan = children[i]
            val result = richSpan.removeTextRange(textRange)
            if (result == null) {
                children.removeAt(i)
            } else {
                children[i] = result
            }
        }

        // Check if the rich span style is empty
        if (text.isEmpty()) {
            if (children.isEmpty()) {
                return null
            } else if (children.size == 1) {
                val child = children.first()
                child.parent = parent
                child.spanStyle = spanStyle.customMerge(child.spanStyle)
                return child
            }
        }

        return this
    }

    /**
     * Get the closest parent rich span with the specified span style
     *
     * @param spanStyle The span style
     * @return The closest parent rich span or null if not found
     */
    fun getClosestRichSpan(spanStyle: SpanStyle): RichSpan? {
        if (spanStyle.isSpecifiedFieldsEquals(this.fullSpanStyle, strict = true)) return this

        return parent?.getClosestRichSpan(spanStyle)
    }

    /**
     * Get the max font size of the children recursively
     *
     * @return The max font size
     */
    fun getMaxFontSize(): TextUnit {
        var height = if (spanStyle.fontSize.isSpecified) spanStyle.fontSize else 0.sp
        children.forEach { richSpan ->
            val childHeight = richSpan.getMaxFontSize()
            if (childHeight.isSpecified && childHeight > height) {
                height = childHeight
            }
        }
        return height
    }

    /**
     * Update the paragraph of the children recursively
     *
     * @param newParagraph The new paragraph
     */
    fun updateChildrenParagraph(newParagraph: RichParagraph) {
        children.forEach { childRichSpan ->
            childRichSpan.paragraph = newParagraph
            childRichSpan.updateChildrenParagraph(newParagraph)
        }
    }

    override fun toString(): String {
        return "richSpan(text='$text', textRange=$textRange, fullTextRange=$fullTextRange)"
    }

    companion object {
        val DefaultSpanStyle = SpanStyle(
            fontSize = 16.sp,
        )
    }
}
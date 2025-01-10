package nl.jolanrensen.example

/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastRoundToInt
import nl.jolanrensen.example.BiasAlignmentDocs.*

/**
 * An interface to calculate the position of box of a certain $DIMENSION inside an available $DIMENSION.
 * $REFERENCE is often used to define the $DIRECTION alignment of a layout inside a
 * parent layout.
 */
@ExcludeFromSources
private interface CommonAlignmentDocs

/**
 * Calculates the $DIRECTION position of a box of $DIMENSION [size] relative to the $SIDE side of
 * an area of $DIMENSION [space]. {@include [OffsetNote]}
 */
@ExcludeFromSources
private interface AlignDocs

/**
 * The returned offset can be negative or larger than `space - size`, meaning that
 * the box will be positioned partially or completely outside the area.
 */
@ExcludeFromSources
private interface OffsetNote

/**
 * An interface to calculate the position of a sized box inside an available space. [Alignment] is
 * often used to define the alignment of a layout inside a parent layout.
 *
 * @see AbsoluteAlignment
 * @see BiasAlignment
 * @see BiasAbsoluteAlignment
 */
@Stable
fun interface Alignment {

    /**
     * Calculates the position of a box of size [size] relative to the top left corner of an area of
     * size [space]. {@include [OffsetNote]}
     */
    fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset

    /**
     * @include [CommonAlignmentDocs]
     * {@set DIMENSION width} {@set DIRECTION horizontal} {@set REFERENCE [Alignment.Horizontal]}
     */
    @Stable
    fun interface Horizontal {

        /** @include [AlignDocs] {@set DIMENSION width} {@set DIRECTION horizontal} {@set SIDE left} */
        fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int

        /**
         * Combine this instance's horizontal alignment with [other]'s vertical alignment to create
         * an [Alignment].
         */
        operator fun plus(other: Vertical): Alignment = CombinedAlignment(this, other)
    }

    /**
     * @include [CommonAlignmentDocs]
     * {@set DIMENSION height} {@set DIRECTION vertical}{@set REFERENCE [Alignment.Vertical]}
     */
    @Stable
    fun interface Vertical {

        /** @include [AlignDocs] {@set DIMENSION height} {@set DIRECTION vertical} {@set SIDE top} */
        fun align(size: Int, space: Int): Int

        /**
         * Combine this instance's vertical alignment with [other]'s horizontal alignment to create
         * an [Alignment].
         */
        operator fun plus(other: Horizontal): Alignment = CombinedAlignment(other, this)
    }

    /** A collection of common [Alignment]s aware of layout direction. */
    companion object {
        // 2D Alignments.
        @Stable
        val TopStart: Alignment = BiasAlignment(-1f, -1f)

        @Stable
        val TopCenter: Alignment = BiasAlignment(0f, -1f)

        @Stable
        val TopEnd: Alignment = BiasAlignment(1f, -1f)

        @Stable
        val CenterStart: Alignment = BiasAlignment(-1f, 0f)

        @Stable
        val Center: Alignment = BiasAlignment(0f, 0f)

        @Stable
        val CenterEnd: Alignment = BiasAlignment(1f, 0f)

        @Stable
        val BottomStart: Alignment = BiasAlignment(-1f, 1f)

        @Stable
        val BottomCenter: Alignment = BiasAlignment(0f, 1f)

        @Stable
        val BottomEnd: Alignment = BiasAlignment(1f, 1f)

        // 1D Alignment.Verticals.
        @Stable
        val Top: Vertical = BiasAlignment.Vertical(-1f)

        @Stable
        val CenterVertically: Vertical = BiasAlignment.Vertical(0f)

        @Stable
        val Bottom: Vertical = BiasAlignment.Vertical(1f)

        // 1D Alignment.Horizontals.
        @Stable
        val Start: Horizontal = BiasAlignment.Horizontal(-1f)

        @Stable
        val CenterHorizontally: Horizontal = BiasAlignment.Horizontal(0f)

        @Stable
        val End: Horizontal = BiasAlignment.Horizontal(1f)
    }
}

private class CombinedAlignment(
    private val horizontal: Alignment.Horizontal,
    private val vertical: Alignment.Vertical,
) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        val x = horizontal.align(size.width, space.width, layoutDirection)
        val y = vertical.align(size.height, space.height)
        return IntOffset(x, y)
    }
}

/** A collection of common [Alignment]s unaware of the layout direction. */
object AbsoluteAlignment {
    // 2D AbsoluteAlignments.
    @Stable
    val TopLeft: Alignment = BiasAbsoluteAlignment(-1f, -1f)

    @Stable
    val TopRight: Alignment = BiasAbsoluteAlignment(1f, -1f)

    @Stable
    val CenterLeft: Alignment = BiasAbsoluteAlignment(-1f, 0f)

    @Stable
    val CenterRight: Alignment = BiasAbsoluteAlignment(1f, 0f)

    @Stable
    val BottomLeft: Alignment = BiasAbsoluteAlignment(-1f, 1f)

    @Stable
    val BottomRight: Alignment = BiasAbsoluteAlignment(1f, 1f)

    // 1D BiasAbsoluteAlignment.Horizontals.
    @Stable
    val Left: Alignment.Horizontal = BiasAbsoluteAlignment.Horizontal(-1f)

    @Stable
    val Right: Alignment.Horizontal = BiasAbsoluteAlignment.Horizontal(1f)
}

/**
 * An $[ALIGNMENT_REF] specified by bias: for example, a bias of -1 represents alignment
 * to the $[START], a bias of 0 will represent centering, and a bias of 1 will represent $[END]. Any
 * value can be specified to obtain an alignment. Inside the [-1, 1] range, the obtained
 * alignment will position the aligned size fully inside the available space, while outside the
 * range it will the aligned size will be positioned partially or completely outside.
 */
@ExcludeFromSources
interface BiasAlignmentDocs {
    interface ALIGNMENT_REF

    interface START

    interface END
}

/**
 * @include [BiasAlignmentDocs]
 * {@set [ALIGNMENT_REF] [Alignment]} {@set [START] start/top} {@set [END] end/bottom}
 * @see BiasAbsoluteAlignment
 * @see Alignment
 */
@Immutable
data class BiasAlignment(val horizontalBias: Float, val verticalBias: Float) : Alignment {

    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        // Convert to Px first and only round at the end, to avoid rounding twice while calculating
        // the new positions
        val centerX = (space.width - size.width).toFloat() / 2f
        val centerY = (space.height - size.height).toFloat() / 2f
        val resolvedHorizontalBias =
            if (layoutDirection == LayoutDirection.Ltr) {
                horizontalBias
            } else {
                -1 * horizontalBias
            }

        val x = centerX * (1 + resolvedHorizontalBias)
        val y = centerY * (1 + verticalBias)
        return IntOffset(x.fastRoundToInt(), y.fastRoundToInt())
    }

    /**
     * @include [BiasAlignmentDocs]
     * {@set [ALIGNMENT_REF] [Alignment.Horizontal]} {@set [START] start} {@set [END] end}
     * @see BiasAbsoluteAlignment.Horizontal
     * @see Vertical
     */
    @Immutable
    data class Horizontal(val bias: Float) : Alignment.Horizontal {
        override fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int {
            // Convert to Px first and only round at the end, to avoid rounding twice while
            // calculating the new positions
            val center = (space - size).toFloat() / 2f
            val resolvedBias = if (layoutDirection == LayoutDirection.Ltr) bias else -1 * bias
            return (center * (1 + resolvedBias)).fastRoundToInt()
        }

        override fun plus(other: Alignment.Vertical): Alignment =
            when (other) {
                is Vertical -> BiasAlignment(bias, other.bias)
                else -> super.plus(other)
            }
    }

    /**
     * @include [BiasAlignmentDocs]
     * {@set [ALIGNMENT_REF] [Alignment.Vertical]} {@set [START] top} {@set [END] bottom}
     * @see Horizontal
     */
    @Immutable
    data class Vertical(val bias: Float) : Alignment.Vertical {
        override fun align(size: Int, space: Int): Int {
            // Convert to Px first and only round at the end, to avoid rounding twice while
            // calculating the new positions
            val center = (space - size).toFloat() / 2f
            return (center * (1 + bias)).fastRoundToInt()
        }

        override fun plus(other: Alignment.Horizontal): Alignment =
            when (other) {
                is Horizontal -> BiasAlignment(other.bias, bias)
                is BiasAbsoluteAlignment.Horizontal -> BiasAbsoluteAlignment(other.bias, bias)
                else -> super.plus(other)
            }
    }
}

/**
 * @include [BiasAlignmentDocs]
 * {@set [ALIGNMENT_REF] [Alignment]} {@set [START] left/top} {@set [END] right/bottom}
 * @see AbsoluteAlignment
 * @see Alignment
 */
@Immutable
data class BiasAbsoluteAlignment(val horizontalBias: Float, val verticalBias: Float) : Alignment {
    /**
     * Returns the position of a 2D point in a container of a given size, according to this
     * [BiasAbsoluteAlignment]. The position will not be mirrored in Rtl context.
     */
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        // Convert to Px first and only round at the end, to avoid rounding twice while calculating
        // the new positions
        val remaining = IntSize(space.width - size.width, space.height - size.height)
        val centerX = remaining.width.toFloat() / 2f
        val centerY = remaining.height.toFloat() / 2f

        val x = centerX * (1 + horizontalBias)
        val y = centerY * (1 + verticalBias)
        return IntOffset(x.fastRoundToInt(), y.fastRoundToInt())
    }

    /**
     * @include [BiasAlignmentDocs]
     * {@set [ALIGNMENT_REF] [Alignment.Horizontal]} {@set [START] left} {@set [END] right}
     * @see BiasAlignment.Horizontal
     */
    @Immutable
    data class Horizontal(val bias: Float) : Alignment.Horizontal {
        /**
         * Returns the position of a 2D point in a container of a given size, according to this
         * [BiasAbsoluteAlignment.Horizontal]. This position will not be mirrored in Rtl context.
         */
        override fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int {
            // Convert to Px first and only round at the end, to avoid rounding twice while
            // calculating the new positions
            val center = (space - size).toFloat() / 2f
            return (center * (1 + bias)).fastRoundToInt()
        }

        override fun plus(other: Alignment.Vertical): Alignment =
            when (other) {
                is BiasAlignment.Vertical -> BiasAbsoluteAlignment(bias, other.bias)
                else -> super.plus(other)
            }
    }
}

package nl.jolanrensen.example

/*
 * Examples from https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/common/src/kotlin/MathH.kt
 */

/**
 * Computes the {@get OP} of the angle [x] given in radians.
 *
 *  Special cases:
 *   - `{@get FUN}(NaN|+Inf|-Inf)` is `NaN`
 */
@ExcludeFromSources
private interface CosSinTanDocs

/**
 * @include [CosSinTanDocs]
 * @set OP cosine
 * @set FUN cos
 */
@ExcludeFromSources
private interface Cos

/**
 * @include [CosSinTanDocs]
 * @set OP sine
 * @set FUN sin
 */
@ExcludeFromSources
private interface Sin

/**
 * @include [CosSinTanDocs]
 * @set OP tangent
 * @set FUN tan
 */
@ExcludeFromSources
private interface Tan

interface MathH {

    /** @include [Cos] */
    fun cos(x: Double): Double

    /** @include [Cos] */
    fun cos(x: Float): Float

    /** @include [Sin] */
    fun sin(x: Double): Double

    /** @include [Sin] */
    fun sin(x: Float): Float

    /** @include [Tan] */
    fun tan(x: Double): Double

    /** @include [Tan] */
    fun tan(x: Float): Float
}

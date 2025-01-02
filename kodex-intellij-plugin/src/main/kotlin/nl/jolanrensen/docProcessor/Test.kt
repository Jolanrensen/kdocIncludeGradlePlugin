package nl.jolanrensen.docProcessor

/**
 * Hello $[A]=You
 */
interface A

/**
 * Hello $[A]=You
 */
fun A.test() {}

/**
 * @include [test]
 * @set [A] Boe
 */
fun main() {
}

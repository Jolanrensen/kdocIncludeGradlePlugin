package nl.jolanrensen.kodex

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.PropertyKey
import kotlin.enums.EnumEntries

private const val MODE = "kodex.mode"
private const val ENABLED = "kodex.enabled"

private const val HIGHLIGHTING = "kodex.highlighting"
private const val COMPLETION = "kodex.completion"

enum class Mode(val id: String) {
    K1("k1"),
    K2("k2"),
}

sealed interface Setting<T> {
    val messageBundleName: String
    val key: String
    val default: T

    fun T.asString(): String

    fun String.asType(): T

    var value: T
        get() = PropertiesComponent.getInstance().getValue(key, default.asString()).asType()
        set(value) {
            PropertiesComponent.getInstance().setValue(key, value.asString())
        }

    operator fun getValue(thisRef: Any?, property: Any?): T = value

    operator fun setValue(thisRef: Any?, property: Any?, value: T) {
        this.value = value
    }
}

sealed interface BooleanSetting : Setting<Boolean> {
    override fun Boolean.asString(): String = toString()

    override fun String.asType(): Boolean = toBoolean()
}

sealed interface EnumSetting<T : Enum<T>> : Setting<T> {
    val values: EnumEntries<T>

    fun setValueAsAny(value: Any) {
        this.value = value as T
    }
}

data object PreprocessorMode : EnumSetting<Mode> {
    override val values: EnumEntries<Mode>
        get() = Mode.entries

    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "mode"
    override val key: String = MODE
    override val default: Mode = Mode.K2

    override fun Mode.asString(): String = name

    override fun String.asType(): Mode = Mode.valueOf(this)
}

data object KodexIsEnabled : BooleanSetting {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "kodexEnabled"
    override val key: String = ENABLED
    override val default: Boolean = true
}

data object KodexHighlightingIsEnabled : BooleanSetting {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "kodexHighlightingEnabled"
    override val key: String = HIGHLIGHTING
    override val default: Boolean = true
}

data object KodexCompletionIsEnabled : BooleanSetting {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "kodexCompletionEnabled"
    override val key: String = COMPLETION
    override val default: Boolean = true
}

val allSettings: Array<Setting<*>> = arrayOf(
    PreprocessorMode,
    KodexIsEnabled,
    KodexHighlightingIsEnabled,
    KodexCompletionIsEnabled,
)

var preprocessorMode by PreprocessorMode
var kodexIsEnabled by KodexIsEnabled
var kodexHighlightingIsEnabled by KodexHighlightingIsEnabled
var kodexCompletionIsEnabled by KodexCompletionIsEnabled

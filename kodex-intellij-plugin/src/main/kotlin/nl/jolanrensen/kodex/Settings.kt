package nl.jolanrensen.kodex

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.whenDisposed
import org.jetbrains.annotations.PropertyKey
import kotlin.enums.EnumEntries

private const val MODE = "kodex.mode"
private const val RENDERING = "kodex.enabled"
private const val INLINE_RENDERING = "kodex.inlineEnabled"

private const val HIGHLIGHTING = "kodex.highlighting"
private const val COMPLETION = "kodex.completion"

enum class Mode(val id: String) {
    K1("k1"),
    K2("k2"),
}

sealed class Setting<T> : ObservableMutableProperty<T> {
    abstract val messageBundleName: String
    abstract val key: String
    abstract val default: T

    abstract fun T.asString(): String

    abstract fun String.asType(): T

    override fun get(): T =
        PropertiesComponent.getInstance()
            .getValue(key, default.asString())
            .asType()

    override fun set(value: T) {
        PropertiesComponent.getInstance().setValue(key, value.asString())
        listeners.forEach { it(value) }
    }

    private val listeners: MutableList<(T) -> Unit> = mutableListOf()

    override fun afterChange(parentDisposable: Disposable?, listener: (T) -> Unit) {
        parentDisposable?.whenDisposed { listeners.remove(listener) }
        listeners += listener
    }

    open val isEnabled: ObservableProperty<Boolean>
        get() = object : ObservableProperty<Boolean> {
            override fun get(): Boolean = true

            override fun afterChange(parentDisposable: Disposable?, listener: (Boolean) -> Unit) {}
        }

    var value: T
        get() = get()
        set(value) {
            set(value)
        }
}

sealed class BooleanSetting : Setting<Boolean>() {
    override fun Boolean.asString(): String = toString()

    override fun String.asType(): Boolean = toBoolean()
}

sealed class EnumSetting<T : Enum<T>> : Setting<T>() {
    abstract val values: EnumEntries<T>

    fun setValueAsAny(value: Any) {
        this.value = value as T
    }
}

data object PreprocessorMode : EnumSetting<Mode>() {
    override val values: EnumEntries<Mode>
        get() = Mode.entries

    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "mode"
    override val key: String = MODE
    override val default: Mode = Mode.K2

    override fun Mode.asString(): String = name

    override fun String.asType(): Mode = Mode.valueOf(this)
}

data object KodexRenderingIsEnabled : BooleanSetting() {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "kodexEnabled"
    override val key: String = INLINE_RENDERING
    override val default: Boolean = true
}

data object KodexInlineRenderingIsEnabled : BooleanSetting() {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "kodexInlineEnabled"
    override val key: String = RENDERING
    override val default: Boolean = true
    override val isEnabled: ObservableProperty<Boolean> = KodexRenderingIsEnabled
}

data object KodexHighlightingIsEnabled : BooleanSetting() {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "kodexHighlightingEnabled"
    override val key: String = HIGHLIGHTING
    override val default: Boolean = true
}

data object KodexCompletionIsEnabled : BooleanSetting() {
    @PropertyKey(resourceBundle = "messages.MessageBundle")
    override val messageBundleName: String = "kodexCompletionEnabled"
    override val key: String = COMPLETION
    override val default: Boolean = true
}

val allSettings: Array<Setting<*>> = arrayOf(
    PreprocessorMode,
    KodexRenderingIsEnabled,
    KodexInlineRenderingIsEnabled,
    KodexHighlightingIsEnabled,
    KodexCompletionIsEnabled,
)

var preprocessorMode by PreprocessorMode
var kodexRenderingIsEnabled by KodexRenderingIsEnabled
var kodexInlineRenderingIsEnabled by KodexInlineRenderingIsEnabled
var kodexHighlightingIsEnabled by KodexHighlightingIsEnabled
var kodexCompletionIsEnabled by KodexCompletionIsEnabled

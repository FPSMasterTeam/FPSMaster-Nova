package top.fpsmaster.module.value.impl

import top.fpsmaster.module.value.Value
import java.util.function.Supplier

/**
 * An ordered, capped list of entries, mapped to Prism `LIST`.
 *
 * Two shapes exist, both from Edge: plain rows carrying an id or text ([items], cap
 * [MAX_ITEMS] like Edge's `MultipleItemSetting`) and keyed rows carrying `{keyCode, message}`
 * ([autoText], cap [MAX_AUTO_TEXT] like Edge's `AutoTextSetting`). [keyed] is what tells the ClickGUI
 * to render the per-row key button and text field.
 */
class ListValue(
    identity: String,
    entries: List<Entry> = emptyList(),
    val capacity: Int = MAX_AUTO_TEXT,
    val keyed: Boolean = false,
    displayable: Supplier<Boolean> = Supplier { true }
) : Value<List<ListValue.Entry>>(identity, entries.take(capacity), displayable) {

    data class Entry(val text: String = "", val keyCode: Int = 0)

    override fun setValue(value: List<Entry>) {
        super.setValue(value.take(capacity).toList())
    }

    fun add(entry: Entry = Entry()): Boolean {
        val current = getValue()
        if (current.size >= capacity) {
            return false
        }
        setValue(current + entry)
        return true
    }

    fun removeAt(index: Int): Boolean {
        val current = getValue()
        if (index !in current.indices) {
            return false
        }
        setValue(current.filterIndexed { position, _ -> position != index })
        return true
    }

    fun setText(index: Int, text: String): Boolean = replace(index) { it.copy(text = text) }

    fun setKey(index: Int, keyCode: Int): Boolean = replace(index) { it.copy(keyCode = keyCode) }

    /** Entry bound to [keyCode], for key-triggered lists such as AutoText. */
    fun forKey(keyCode: Int): Entry? = getValue().firstOrNull { it.keyCode == keyCode && it.keyCode != 0 }

    fun texts(): List<String> = getValue().map { it.text }

    private inline fun replace(index: Int, edit: (Entry) -> Entry): Boolean {
        val current = getValue()
        if (index !in current.indices) {
            return false
        }
        setValue(current.mapIndexed { position, entry -> if (position == index) edit(entry) else entry })
        return true
    }

    companion object {
        const val MAX_ITEMS = 7
        const val MAX_AUTO_TEXT = 20

        fun items(
            identity: String,
            ids: List<String> = emptyList(),
            displayable: Supplier<Boolean> = Supplier { true }
        ): ListValue = ListValue(identity, ids.map { Entry(it) }, MAX_ITEMS, false, displayable)

        fun autoText(
            identity: String,
            entries: List<Entry> = emptyList(),
            displayable: Supplier<Boolean> = Supplier { true }
        ): ListValue = ListValue(identity, entries, MAX_AUTO_TEXT, true, displayable)
    }
}

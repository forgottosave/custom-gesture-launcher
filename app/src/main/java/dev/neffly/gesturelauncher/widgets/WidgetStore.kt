package dev.neffly.gesturelauncher.widgets

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One widget on the widgets page: the id the host allocated for it, and the height it was
 *  given, in dp so it survives a density change. */
@Serializable
data class WidgetEntry(val id: Int, val heightDp: Int)

/**
 * Each widget page's list, in page order.
 *
 * Kept apart from the launcher backup on purpose: an app-widget id only means something to the
 * host that allocated it on the device that allocated it, so there is nothing here worth carrying
 * to another phone.
 */
object WidgetStore {

    private const val FILE = "widgets"
    private const val KEY_ENTRIES = "entries"

    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context, storeKey: String): List<WidgetEntry> {
        val raw = prefs(context, storeKey).getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<WidgetEntry>>(raw) }.getOrDefault(emptyList())
    }

    fun save(context: Context, storeKey: String, entries: List<WidgetEntry>) {
        prefs(context, storeKey).edit().putString(KEY_ENTRIES, json.encodeToString(entries)).apply()
    }

    private fun prefs(context: Context, storeKey: String) = context.getSharedPreferences(
        if (storeKey == "left") FILE else "${FILE}_$storeKey",
        Context.MODE_PRIVATE
    )
}

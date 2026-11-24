package com.example.iperfdroid

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One quick test item: [name] is shown on the home screen card, [args] are the iperf3 arguments,
 * for example "-c 192.168.5.200 -t 10".
 */
data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val args: String
) {
    /** Full command shown to the user, so it is obvious at a glance what this item runs. */
    val displayCommand: String
        get() = "iperf3 $args".trim()
}

class ShortcutStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

    fun load(): MutableList<Shortcut> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return defaultShortcuts().toMutableList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Shortcut>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Shortcut(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        name = o.optString("name"),
                        args = o.optString("args")
                    )
                )
            }
            list
        } catch (e: Exception) {
            defaultShortcuts().toMutableList()
        }
    }

    fun save(list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("name", it.name)
            o.put("args", it.args)
            arr.put(o)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun defaultShortcuts(): List<Shortcut> = listOf(
        Shortcut(name = appContext.getString(R.string.default_shortcut_server), args = "-c 192.168.5.200"),
        Shortcut(name = appContext.getString(R.string.default_shortcut_reverse), args = "-c 192.168.5.200 -R"),
        Shortcut(name = appContext.getString(R.string.default_shortcut_json), args = "-c 192.168.5.200 -t 10 -J")
    )

    companion object {
        private const val KEY_ITEMS = "items"
    }
}

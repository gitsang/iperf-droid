package com.example.iperftv

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 一个快捷测试项：name 显示在首页按钮上，args 是 iperf3 的参数，例如 "-c 192.168.5.200 -t 10"。
 */
data class Shortcut(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val args: String
) {
    /** 用于展示的完整命令，便于一眼确认这条快捷项到底跑什么。 */
    val displayCommand: String
        get() = "iperf3 $args".trim()
}

class ShortcutStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("shortcuts", Context.MODE_PRIVATE)

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
        Shortcut(name = "默认服务器", args = "-c 192.168.5.200"),
        Shortcut(name = "反向测试", args = "-c 192.168.5.200 -R"),
        Shortcut(name = "JSON 10 秒", args = "-c 192.168.5.200 -t 10 -J")
    )

    companion object {
        private const val KEY_ITEMS = "items"
    }
}

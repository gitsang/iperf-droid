package com.example.iperftv

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 配置页：右上角进入，维护快捷命令列表（新增 / 编辑 / 删除）。
 */
class ConfigActivity : AppCompatActivity() {

    private lateinit var store: ShortcutStore
    private val items = mutableListOf<Shortcut>()
    private lateinit var adapter: ConfigAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)
        store = ShortcutStore(this)

        adapter = ConfigAdapter()
        findViewById<RecyclerView>(R.id.recyclerConfig).apply {
            layoutManager = LinearLayoutManager(this@ConfigActivity)
            adapter = this@ConfigActivity.adapter
        }

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.btnAdd).setOnClickListener { showEditDialog(null) }
        findViewById<TextView>(R.id.btnSelfCheck).setOnClickListener { runSelfCheck() }
    }

    /** 环境自检：确认内置 iperf3 能在这台电视上跑起来。 */
    private fun runSelfCheck() {
        val dlg = AlertDialog.Builder(this)
            .setTitle(R.string.action_selfcheck)
            .setMessage(getString(R.string.selfcheck_running))
            .setCancelable(false)
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.dismiss() }
            .create()
        dlg.show()

        SelfCheck(this).run { result ->
            runOnUiThread {
                if (dlg.isShowing) dlg.dismiss()
                val text = buildString {
                    append(result.summary).append("\n\n")
                    result.lines.forEach { append(it).append("\n") }
                }
                AlertDialog.Builder(this)
                    .setTitle(R.string.action_selfcheck)
                    .setMessage(text)
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        items.clear()
        items.addAll(store.load())
        adapter.notifyDataSetChanged()

        // 从首页长按进入时，直接弹出该条目的编辑框
        val editId = intent.getStringExtra(EXTRA_EDIT_ID)
        if (editId != null) {
            intent.removeExtra(EXTRA_EDIT_ID)
            items.firstOrNull { it.id == editId }?.let { showEditDialog(it) }
        }
    }

    private fun persist() {
        store.save(items)
    }

    private fun showEditDialog(target: Shortcut?) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_shortcut, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etArgs = view.findViewById<EditText>(R.id.etArgs)
        etName.setText(target?.name ?: "")
        etArgs.setText(target?.args ?: "-c 192.168.5.200")

        // 常用模板，点一下直接填入参数，省去遥控器打字
        val presets = listOf(
            "-c 192.168.5.200",
            "-c 192.168.5.200 -t 10",
            "-c 192.168.5.200 -t 10 -J",
            "-c 192.168.5.200 -R",
            "-c 192.168.5.200 -u -b 100M",
            "-s"
        )
        val presetAdapter = PresetAdapter(presets) { preset ->
            etArgs.setText(preset)
            etArgs.setSelection(preset.length)
        }
        view.findViewById<RecyclerView>(R.id.recyclerPreset).apply {
            layoutManager = LinearLayoutManager(this@ConfigActivity)
            adapter = presetAdapter
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (target == null) R.string.action_add else R.string.action_edit)
            .setView(view)
            .setPositiveButton(R.string.dialog_ok, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                val name = etName.text.toString().trim()
                val args = etArgs.text.toString().trim()
                when {
                    name.isEmpty() -> toast(getString(R.string.msg_name_required))
                    args.isEmpty() -> toast(getString(R.string.msg_args_required))
                    else -> {
                        if (target == null) {
                            items.add(Shortcut(name = name, args = args))
                        } else {
                            val idx = items.indexOfFirst { it.id == target.id }
                            if (idx >= 0) {
                                items[idx] = target.copy(name = name, args = args)
                            }
                        }
                        persist()
                        adapter.notifyDataSetChanged()
                        toast(getString(R.string.msg_saved))
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    /** 上移/下移快捷项，delta = -1 上移，+1 下移。 */
    private fun move(item: Shortcut, delta: Int) {
        val idx = items.indexOfFirst { it.id == item.id }
        val target = idx + delta
        if (idx < 0 || target < 0 || target >= items.size) return
        val tmp = items[idx]
        items[idx] = items[target]
        items[target] = tmp
        persist()
        adapter.notifyItemMoved(idx, target)
    }

    private fun confirmDelete(item: Shortcut) {        AlertDialog.Builder(this)
            .setTitle(R.string.action_delete)
            .setMessage(getString(R.string.msg_delete_confirm) + "\n" + item.displayCommand)
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                items.removeAll { it.id == item.id }
                persist()
                adapter.notifyDataSetChanged()
                toast(getString(R.string.msg_deleted))
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private inner class ConfigAdapter : RecyclerView.Adapter<ConfigAdapter.VH>() {        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_config, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val name: TextView = view.findViewById(R.id.tvCfgName)
            private val args: TextView = view.findViewById(R.id.tvCfgArgs)

            fun bind(item: Shortcut) {
                name.text = item.name
                args.text = item.displayCommand
                itemView.findViewById<TextView>(R.id.btnUp).setOnClickListener {
                    move(item, -1)
                }
                itemView.findViewById<TextView>(R.id.btnDown).setOnClickListener {
                    move(item, 1)
                }
                itemView.findViewById<TextView>(R.id.btnEdit).setOnClickListener {
                    showEditDialog(item)
                }
                itemView.findViewById<TextView>(R.id.btnDelete).setOnClickListener {
                    confirmDelete(item)
                }
            }
        }
    }

    private class PresetAdapter(
        private val data: List<String>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<PresetAdapter.VH>() {
        override fun getItemCount() = data.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_preset, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(data[position])

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val tv: TextView = view.findViewById(R.id.tvPreset)

            fun bind(text: String) {
                tv.text = text
                tv.setOnClickListener { onClick(text) }
            }
        }
    }

    companion object {
        const val EXTRA_EDIT_ID = "extra_edit_id"
    }
}

package com.example.iperftv

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 首页：网格展示所有快捷命令，方向键在网格中移动，OK 键执行测试。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var store: ShortcutStore
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView
    private val adapter = ShortcutAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = ShortcutStore(this)

        recycler = findViewById(R.id.recyclerShortcuts)
        emptyView = findViewById(R.id.tvEmpty)

        recycler.layoutManager = GridLayoutManager(this, 4)
        recycler.adapter = adapter
        recycler.setHasFixedSize(true)

        findViewById<TextView>(R.id.btnConfig).setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(store.load())
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        // 让焦点默认落在第一个卡片上，遥控器按 OK 即可直接开测
        if (adapter.itemCount > 0) {
            recycler.post { recycler.getChildAt(0)?.requestFocus() }
        } else {
            findViewById<TextView>(R.id.btnConfig).requestFocus()
        }
    }

    private inner class ShortcutAdapter : RecyclerView.Adapter<ShortcutAdapter.VH>() {
        private val items = mutableListOf<Shortcut>()

        fun submit(list: List<Shortcut>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_shortcut, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position])
        }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            private val name: TextView = view.findViewById(R.id.tvItemName)
            private val args: TextView = view.findViewById(R.id.tvItemArgs)

            fun bind(item: Shortcut) {
                name.text = item.name
                args.text = item.displayCommand
                itemView.setOnClickListener {
                    val intent = Intent(this@MainActivity, RunActivity::class.java)
                        .putExtra(RunActivity.EXTRA_TITLE, item.name)
                        .putExtra(RunActivity.EXTRA_ARGS, item.args)
                    startActivity(intent)
                }
                // 长按直接进入编辑，省得到配置页再找一遍
                itemView.setOnLongClickListener {
                    val intent = Intent(this@MainActivity, ConfigActivity::class.java)
                        .putExtra(ConfigActivity.EXTRA_EDIT_ID, item.id)
                    startActivity(intent)
                    true
                }
            }
        }
    }
}

package top.c8g.iperf_droid

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Home screen: shows every shortcut in a grid. Arrow keys move within the grid, the OK key runs
 * the test.
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

        recycler.layoutManager = GridLayoutManager(this, spanCount())
        recycler.adapter = adapter
        recycler.setHasFixedSize(true)

        // A rotation changes how many cards fit per row, so rebuild the grid with the new width
        recycler.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val span = spanCount()
            (recycler.layoutManager as? GridLayoutManager)?.let {
                if (it.spanCount != span) it.spanCount = span
            }
        }

        findViewById<ImageView>(R.id.btnConfig).setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.submit(store.load())
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
        // Put focus on the first card so the remote's OK key starts a test directly
        if (adapter.itemCount > 0) {
            recycler.post { recycler.getChildAt(0)?.requestFocus() }
        } else {
            findViewById<ImageView>(R.id.btnConfig).requestFocus()
        }
    }

    /**
     * Cards per row, derived from the available width rather than hard-coded to 4. A phone in
     * portrait only fits one or two columns; a TV in landscape fits four.
     */
    private fun spanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return (widthDp / CARD_MIN_WIDTH_DP).coerceAtLeast(1)
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
                // Long press jumps straight into editing, so there is no need to find it again on
                // the config screen
                itemView.setOnLongClickListener {
                    val intent = Intent(this@MainActivity, ConfigActivity::class.java)
                        .putExtra(ConfigActivity.EXTRA_EDIT_ID, item.id)
                    startActivity(intent)
                    true
                }
            }
        }
    }

    companion object {
        /**
         * Narrowest a card may get before its two lines of text look cramped. 240dp keeps a
         * 1080p TV at the original four columns while a phone in portrait falls back to one.
         */
        private const val CARD_MIN_WIDTH_DP = 240
    }
}

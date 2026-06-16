package com.example.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R

class ClipboardAdapter(
    private val onItemClicked: (ClipboardItem) -> Unit,
    private val onItemLongClicked: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.ClipboardViewHolder>() {

    private val items = mutableListOf<ClipboardItem>()

    fun setItems(newItems: List<ClipboardItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipboardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_clipboard, parent, false)
        return ClipboardViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipboardViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount() = items.size

    inner class ClipboardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvClipText: TextView = itemView.findViewById(R.id.tv_clip_text)
        private val ivPin: ImageView = itemView.findViewById(R.id.iv_pin)

        fun bind(item: ClipboardItem) {
            tvClipText.text = item.text
            ivPin.visibility = if (item.isPinned) View.VISIBLE else View.GONE
            
            // For a pinned item, we could show a filled pin, for unpinned a hidden pin or outlined pin
            if (item.isPinned) {
                ivPin.setImageResource(android.R.drawable.star_on)
            }

            itemView.setOnClickListener {
                onItemClicked(item)
            }
            itemView.setOnLongClickListener {
                onItemLongClicked(item)
                true
            }
        }
    }
}

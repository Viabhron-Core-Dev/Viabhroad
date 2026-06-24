package com.example.keyboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.R

class EmojiAdapter(private var emojis: List<String>, private val onClick: (String) -> Unit) : RecyclerView.Adapter<EmojiAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = emojis[position]
        holder.itemView.setOnClickListener { onClick(emojis[position]) }
    }

    override fun getItemCount() = emojis.size
    
    fun updateData(newEmojis: List<String>) {
        emojis = newEmojis
        notifyDataSetChanged()
    }
}

package com.miappvideos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.miappvideos.R

class SearchHistoryAdapter(
    private val onItemClick: (String) -> Unit,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.HistoryViewHolder>() {

    private val queries = mutableListOf<String>()

    fun update(newQueries: List<String>) {
        queries.clear()
        queries.addAll(newQueries)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val query = queries[position]
        holder.tvQuery.text = query
        holder.itemView.setOnClickListener { onItemClick(query) }
        holder.btnDelete.setOnClickListener { onDeleteClick(query) }
    }

    override fun getItemCount() = queries.size

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvQuery: TextView = itemView.findViewById(R.id.tvQuery)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }
}

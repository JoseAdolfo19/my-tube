package com.miappvideos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.miappvideos.R
import com.miappvideos.model.PipedVideo

class QueueAdapter(
    private var videos: List<PipedVideo>,
    private var currentIndex: Int = -1,
    private val onItemClick: (Int) -> Unit,
    private val onOptionsClick: (Int) -> Unit = {}
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    fun updateQueue(newVideos: List<PipedVideo>, newCurrentIndex: Int) {
        videos = newVideos
        currentIndex = newCurrentIndex
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video, position == currentIndex)
        holder.itemView.setOnClickListener { onItemClick(position) }
        holder.btnOptions.setOnClickListener { onOptionsClick(position) }
    }

    override fun getItemCount() = videos.size

    class QueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnOptions: ImageView = itemView.findViewById(R.id.btnQueueOptions)
        private val thumbnail: ImageView = itemView.findViewById(R.id.queueThumbnail)
        private val title: TextView = itemView.findViewById(R.id.queueTitle)
        private val channel: TextView = itemView.findViewById(R.id.queueChannel)

        fun bind(video: PipedVideo, isCurrent: Boolean) {
            title.text = video.title
            channel.text = video.uploaderName ?: "Desconocido"
            itemView.setBackgroundResource(
                if (isCurrent) R.color.queue_current
                else android.R.color.transparent
            )
            video.thumbnail?.let { url ->
                thumbnail.load(url) {
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            }
        }
    }
}

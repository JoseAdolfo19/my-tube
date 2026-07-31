package com.miappvideos.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.miappvideos.R
import com.miappvideos.model.PipedVideo
import java.text.SimpleDateFormat
import java.util.Locale

class VideoAdapter(
    private var videos: List<PipedVideo>,
    private val onVideoClick: (PipedVideo) -> Unit,
    private val onOptionsClick: (PipedVideo) -> Unit = {}
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    fun updateVideos(newVideos: List<PipedVideo>) {
        videos = newVideos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video)
        holder.itemView.setOnClickListener { onVideoClick(video) }
        holder.btnOptions.setOnClickListener { onOptionsClick(video) }
    }

    override fun getItemCount() = videos.size

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnOptions: ImageView = itemView.findViewById(R.id.btnOptions)
        private val thumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val channel: TextView = itemView.findViewById(R.id.tvChannel)
        private val duration: TextView = itemView.findViewById(R.id.tvDuration)

        fun bind(video: PipedVideo) {
            title.text = video.title
            val parts = mutableListOf(video.uploaderName ?: "Desconocido")
            val viewsText = formatViews(video.views)
            if (viewsText.isNotEmpty()) parts.add(viewsText)
            val dateText = formatDate(video.uploadedDate)
            if (dateText.isNotEmpty()) parts.add(dateText)
            channel.text = parts.joinToString(" • ")

            val durationText = formatDuration(video.duration)
            if (durationText.isNotEmpty()) {
                duration.text = durationText
                duration.visibility = View.VISIBLE
            } else {
                duration.visibility = View.GONE
            }

            video.thumbnail?.let { url ->
                thumbnail.load(url) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(12f))
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            }
        }

        private fun formatDuration(seconds: Long?): String {
            if (seconds == null || seconds <= 0) return ""
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
            else String.format(Locale.US, "%d:%02d", m, s)
        }

        private fun formatViews(views: Long?): String {
            if (views == null) return ""
            return when {
                views >= 1_000_000_000 -> "${views / 1_000_000_000}B"
                views >= 1_000_000 -> "${views / 1_000_000}M"
                views >= 1_000 -> "${views / 1_000}K"
                else -> "$views"
            }
        }

        private fun formatDate(iso: String?): String {
            if (iso == null) return ""
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val date = parser.parse(iso) ?: return ""
                val days = (System.currentTimeMillis() - date.time) / (1000 * 60 * 60 * 24)
                when {
                    days < 1 -> "hoy"
                    days == 1L -> "ayer"
                    days < 30 -> "hace $days días"
                    days < 365 -> "hace ${days / 30} meses"
                    else -> "hace ${days / 365} años"
                }
            } catch (_: Exception) {
                ""
            }
        }
    }
}

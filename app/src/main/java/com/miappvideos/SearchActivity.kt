package com.miappvideos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.miappvideos.adapter.SearchHistoryAdapter
import com.miappvideos.adapter.VideoAdapter
import com.miappvideos.api.PipedApi
import com.miappvideos.api.YouTubeDataManager
import com.miappvideos.model.PipedVideo
import com.miappvideos.util.toPipedVideo
import kotlinx.coroutines.launch
import org.json.JSONArray

class SearchActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var historyRecycler: RecyclerView
    private lateinit var resultsRecycler: RecyclerView
    private lateinit var historyHeader: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var historyAdapter: SearchHistoryAdapter
    private lateinit var resultsAdapter: VideoAdapter
    private lateinit var api: PipedApi
    private lateinit var youTubeManager: YouTubeDataManager

    private val history = mutableListOf<String>()

    companion object {
        private const val PREFS_NAME = "search_history_prefs"
        private const val KEY_HISTORY = "history_json"
        private const val MAX_HISTORY = 20

        const val EXTRA_URL = "video_url"
        const val EXTRA_TITLE = "video_title"
        const val EXTRA_THUMB = "video_thumb"
        const val EXTRA_UPLOADER = "video_uploader"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        searchInput = findViewById(R.id.searchInput)
        historyRecycler = findViewById(R.id.historyRecycler)
        resultsRecycler = findViewById(R.id.resultsRecycler)
        historyHeader = findViewById(R.id.historyHeader)
        emptyView = findViewById(R.id.emptyView)
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnClearInput).setOnClickListener {
            searchInput.text?.clear()
            showHistory()
        }
        findViewById<TextView>(R.id.btnClearAll).setOnClickListener {
            history.clear()
            saveHistory()
            refreshHistory()
        }

        api = PipedApi.create()
        youTubeManager = YouTubeDataManager(this)

        historyAdapter = SearchHistoryAdapter(
            onItemClick = { query ->
                searchInput.setText(query)
                searchInput.setSelection(query.length)
                searchVideos(query)
            },
            onDeleteClick = { query ->
                history.remove(query)
                saveHistory()
                refreshHistory()
            }
        )
        historyRecycler.layoutManager = LinearLayoutManager(this)
        historyRecycler.adapter = historyAdapter

        resultsAdapter = VideoAdapter(
            videos = emptyList(),
            onVideoClick = { video -> returnVideo(video) },
            onOptionsClick = {}
        )
        resultsRecycler.layoutManager = LinearLayoutManager(this)
        resultsRecycler.adapter = resultsAdapter

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) searchVideos(query)
                true
            } else false
        }

        loadHistory()
        refreshHistory()
        searchInput.requestFocus()
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                history.add(arr.getString(i))
            }
        } catch (_: Exception) {}
    }

    private fun saveHistory() {
        val arr = JSONArray()
        for (q in history) arr.put(q)
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    private fun addToHistory(query: String) {
        history.remove(query)
        history.add(0, query)
        if (history.size > MAX_HISTORY) {
            history.removeAt(history.lastIndex)
        }
        saveHistory()
    }

    private fun refreshHistory() {
        historyAdapter.update(history)
        historyRecycler.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        emptyView.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showHistory() {
        historyHeader.visibility = View.VISIBLE
        historyRecycler.visibility = View.VISIBLE
        resultsRecycler.visibility = View.GONE
        resultsAdapter.updateVideos(emptyList())
        refreshHistory()
    }

    private fun searchVideos(query: String) {
        addToHistory(query)
        historyHeader.visibility = View.GONE
        historyRecycler.visibility = View.GONE
        emptyView.visibility = View.GONE
        resultsRecycler.visibility = View.VISIBLE
        refreshHistory()

        lifecycleScope.launch {
            try {
                val ytVideos = youTubeManager.searchYouTube(query)
                if (ytVideos.isNotEmpty()) {
                    resultsAdapter.updateVideos(ytVideos.map { it.toPipedVideo() })
                    return@launch
                }
            } catch (_: Exception) {}

            try {
                val result = api.search(query)
                resultsAdapter.updateVideos(result.items.take(30))
            } catch (e: Exception) {
                Toast.makeText(this@SearchActivity, "Error al buscar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun returnVideo(video: PipedVideo) {
        val intent = Intent()
        intent.putExtra(EXTRA_URL, video.url)
        intent.putExtra(EXTRA_TITLE, video.title)
        intent.putExtra(EXTRA_THUMB, video.thumbnail)
        intent.putExtra(EXTRA_UPLOADER, video.uploaderName)
        setResult(RESULT_OK, intent)
        finish()
    }
}

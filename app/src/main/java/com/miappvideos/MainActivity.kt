package com.miappvideos

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintSet
import coil.load
import coil.transform.CircleCropTransformation
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.miappvideos.adapter.PlaylistAdapter
import com.miappvideos.adapter.VideoAdapter
import com.miappvideos.api.PipedApi
import com.miappvideos.api.YouTubeDataManager
import com.miappvideos.download.DownloadService
import com.miappvideos.model.PipedVideo
import com.miappvideos.model.YouTubeVideo
import com.miappvideos.util.DataSaver
import com.miappvideos.util.RecommendationEngine
import com.miappvideos.util.toPipedVideo
import com.miappvideos.player.ExoPlayerHolder
import com.miappvideos.player.ExoPlayerManager
import com.miappvideos.player.PlayerService
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerVideos: RecyclerView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var playerContainer: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var titleTextView: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnBackground: ImageButton
    private lateinit var btnPip: ImageButton
    private lateinit var btnSearch: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var btnCreate: ImageButton
    private lateinit var btnAvatar: ImageView
    private lateinit var chipGroup: com.google.android.material.chip.ChipGroup
    private lateinit var chipsRow: HorizontalScrollView
    private lateinit var searchLayout: TextInputLayout
    private lateinit var searchInput: TextInputEditText
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rootLayout: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var queueRecyclerView: RecyclerView
    private lateinit var queueAdapter: com.miappvideos.adapter.QueueAdapter

    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniThumbnail: ImageView
    private lateinit var miniTitle: TextView

    private lateinit var btnQuality: TextView
    private lateinit var btnLyrics: TextView
    private var currentAudioUrl: String? = null
    private var currentVideoUrl: String? = null
    private var currentArtist: String? = null
    private var currentVideoQualities: List<com.miappvideos.api.innertube.StreamResolver.VideoQuality> = emptyList()
    private lateinit var miniChannel: TextView
    private lateinit var miniPrev: ImageButton
    private lateinit var miniPlayPause: ImageButton
    private lateinit var miniNext: ImageButton
    private lateinit var miniExpand: ImageButton
    private lateinit var fullPlayerContainer: LinearLayout
    private lateinit var playerControlsBar: LinearLayout
    private lateinit var btnCollapse: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private var seeking = false

    private lateinit var api: PipedApi
    private lateinit var youTubeManager: YouTubeDataManager
    private lateinit var playerManager: ExoPlayerManager
    private lateinit var adapter: VideoAdapter
    private lateinit var recommendationEngine: com.miappvideos.util.RecommendationEngine

    private var isPlaying = false
    private var isInPipMode = false
    private var isBackgroundMode = false
    private var searchVisible = false
    private var isSignedIn = false
    private var currentEmail: String? = null
    private var currentName: String = "Invitado"
    private var currentPhotoUrl: String? = null
    private var isLoading = false
    private var refreshIndex = 0
    private var isExpanded = false
    private var isShuffle = false
    private var repeatMode = 0
    private val videoQueue = mutableListOf<com.miappvideos.model.PipedVideo>()
    private var currentQueueIndex = -1
    private var extendingQueue = false
    private val watchHistory = mutableListOf<com.miappvideos.model.PipedVideo>()

    private val autoplayQueries = listOf(
        "música popular", "mixes", "cumbia", "salsa", "reggaetón",
        "rock en español", "baladas", "bachata", "vallenato", "electrónica"
    )

    companion object {
        private const val RC_LOGIN = 9002
        private const val RC_SEARCH = 9003
        private const val RC_DOWNLOAD = 9004
        private const val PERMISSION_STORAGE = 200
        private var pendingDownload: Triple<com.miappvideos.model.PipedVideo, com.miappvideos.api.innertube.StreamResolver.DownloadOption, String>? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        api = PipedApi.create()
        playerManager = ExoPlayerManager(this)
        ExoPlayerHolder.player = playerManager
        youTubeManager = YouTubeDataManager(this)
        recommendationEngine = com.miappvideos.util.RecommendationEngine(api, youTubeManager)

        currentName = intent.getStringExtra("user_name") ?: "Invitado"
        currentEmail = intent.getStringExtra("user_email")
        currentPhotoUrl = intent.getStringExtra("user_photo")
        isSignedIn = currentEmail != null

        bindViews()

        requestNotificationPermission()

        titleTextView.text = "Bienvenido, $currentName"

        if (isSignedIn) {
            lifecycleScope.launch {
                youTubeManager.refreshToken(currentEmail)
            }
        }
        setupPlayerView()
        setupRecyclerView()
        setupControls()
        setupSwipeRefresh()
        setupQueue()
        setupProgress()
        loadWatchHistory()
        loadAvatar()
        updatePipButtonVisibility()

        lifecycleScope.launch {
            com.miappvideos.api.innertube.StreamResolver.ensureVisitorData()
        }

        loadTrending()
    }

    override fun onResume() {
        super.onResume()
        loadTrending()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SEARCH && resultCode == RESULT_OK && data != null) {
            val url = data.getStringExtra(SearchActivity.EXTRA_URL)
            val title = data.getStringExtra(SearchActivity.EXTRA_TITLE)
            if (!url.isNullOrEmpty() && !title.isNullOrEmpty()) {
                playVideo(
                    com.miappvideos.model.PipedVideo(
                        url = url,
                        title = title,
                        thumbnail = data.getStringExtra(SearchActivity.EXTRA_THUMB),
                        uploaderName = data.getStringExtra(SearchActivity.EXTRA_UPLOADER),
                        uploaderAvatar = null,
                        uploadedDate = null,
                        shortDescription = null,
                        duration = null,
                        views = null,
                        uploaderVerified = null
                    )
                )
            }
        }
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        toolbar = findViewById(R.id.toolbar)
        recyclerVideos = findViewById(R.id.recyclerVideos)
        playerContainer = findViewById(R.id.playerContainer)
        playerView = findViewById(R.id.playerView)
        btnQuality = findViewById(R.id.btnQuality)
        btnLyrics = findViewById(R.id.btnLyrics)
        titleTextView = findViewById(R.id.titleTextView)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnPrev = findViewById(R.id.btnPrev)
        btnNext = findViewById(R.id.btnNext)
        btnBackground = findViewById(R.id.btnBackground)
        btnPip = findViewById(R.id.btnPip)
        btnSearch = findViewById(R.id.btnSearch)
        btnMenu = findViewById(R.id.btnMenu)
        btnCreate = findViewById(R.id.btnCreate)
        btnAvatar = findViewById(R.id.btnAvatar)
        chipGroup = findViewById(R.id.chipGroup)
        chipsRow = findViewById(R.id.chipsRow)
        searchLayout = findViewById(R.id.searchLayout)
        searchInput = findViewById(R.id.searchInput)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        queueRecyclerView = findViewById(R.id.queueRecyclerView)

        miniPlayer = findViewById(R.id.miniPlayer)
        miniThumbnail = findViewById(R.id.miniThumbnail)
        miniTitle = findViewById(R.id.miniTitle)
        miniChannel = findViewById(R.id.miniChannel)
        miniPrev = findViewById(R.id.miniPrev)
        miniPlayPause = findViewById(R.id.miniPlayPause)
        miniNext = findViewById(R.id.miniNext)
        miniExpand = findViewById(R.id.miniExpand)
        fullPlayerContainer = findViewById(R.id.fullPlayerContainer)
        playerControlsBar = findViewById(R.id.playerControlsBar)
        btnCollapse = findViewById(R.id.btnCollapse)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        seekBar = findViewById(R.id.seekBar)
    }

    private fun setupPlayerView() {
        playerView.player = playerManager.player

        playerManager.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlay: Boolean) {
                isPlaying = isPlay
                updatePlayPauseIcon()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playerContainer.visibility = View.VISIBLE
                } else if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val sb = StringBuilder("Playback error: ${error.message}\n")
                var cause: Throwable? = error
                while (cause != null) {
                    sb.append("  Caused by: ${cause.javaClass.simpleName}: ${cause.message}\n")
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        cause.responseBody?.let { sb.append("  RESPONSE BODY: ${String(it)}\n") }
                    }
                    cause = cause.cause
                }
                Log.d("PlayerError", sb.toString())
            }
        })

        miniPlayer.setOnClickListener { togglePlayerMode() }

        val swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isExpanded) {
                    togglePlayerMode()
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                return if (isExpanded) {
                    if (abs(dy) > abs(dx) && abs(dy) > 100 && velocityY > 400) {
                        togglePlayerMode()
                        true
                    } else {
                        false
                    }
                } else {
                    if (abs(dx) > abs(dy) && abs(dx) > 100 && abs(velocityX) > 400) {
                        if (velocityX < 0) nextVideo() else prevVideo()
                        true
                    } else {
                        false
                    }
                }
            }
        })
        miniPlayer.setOnTouchListener { _, event -> swipeDetector.onTouchEvent(event) }
        playerView.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            false
        }
        playerControlsBar.setOnTouchListener { _, event ->
            swipeDetector.onTouchEvent(event)
            false
        }

        miniPlayPause.setOnClickListener {
            playerManager.isPlaying = !playerManager.isPlaying
        }

        miniPrev.setOnClickListener { prevVideo() }
        miniNext.setOnClickListener { nextVideo() }
        miniExpand.setOnClickListener { togglePlayerMode() }
        btnCollapse.setOnClickListener { togglePlayerMode() }

        playerManager.onNext = { nextVideo() }
        playerManager.onPrevious = { prevVideo() }
    }

    private fun setupRecyclerView() {
        adapter = VideoAdapter(
            emptyList(),
            onVideoClick = { video -> playVideo(video) },
            onOptionsClick = { video -> showVideoOptions(video) }
        )
        recyclerVideos.layoutManager = LinearLayoutManager(this)
        recyclerVideos.adapter = adapter
        recyclerVideos.setHasFixedSize(true)
    }

    private fun setupQueue() {
        queueAdapter = com.miappvideos.adapter.QueueAdapter(
            emptyList(),
            -1,
            onItemClick = { index -> playQueueItem(index) },
            onOptionsClick = { index -> showQueueOptions(index) }
        )
        queueRecyclerView.layoutManager = LinearLayoutManager(this)
        queueRecyclerView.adapter = queueAdapter
    }

    private fun refreshQueue() {
        queueAdapter.updateQueue(videoQueue.toList(), currentQueueIndex)
        if (currentQueueIndex >= 0) {
            queueRecyclerView.scrollToPosition(currentQueueIndex)
        }
    }

    private fun showVideoOptions(video: com.miappvideos.model.PipedVideo) {
        val options = arrayOf(
            "Reproducir",
            "Agregar a la cola",
            "Descargar música (elegir calidad)",
            "Descargar video (elegir calidad)"
        )
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(video.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playVideo(video)
                    1 -> {
                        val videoId = extractVideoId(video.url.orEmpty())
                        if (videoQueue.none { extractVideoId(it.url.orEmpty()) == videoId }) {
                            videoQueue.add(video)
                            Toast.makeText(this, "Agregado a la cola", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Ya está en la cola", Toast.LENGTH_SHORT).show()
                        }
                    }
                    2 -> startDownload(video, DownloadService.MODE_AUDIO)
                    3 -> startDownload(video, DownloadService.MODE_VIDEO)
                }
            }
            .show()
    }

    private fun startDownload(video: com.miappvideos.model.PipedVideo, mode: String) {
        val videoId = extractVideoId(video.url.orEmpty())
        if (videoId.isEmpty()) {
            Toast.makeText(this, "Video no válido para descargar", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Buscando calidades...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val options = com.miappvideos.api.innertube.StreamResolver
                .resolveDownloadOptions(videoId).getOrNull()
            if (options == null) {
                Toast.makeText(this@MainActivity, "No se pudieron obtener las opciones", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val list = if (mode == DownloadService.MODE_VIDEO) options.video else options.audio
            if (list.isEmpty()) {
                Toast.makeText(this@MainActivity, "No hay opciones disponibles", Toast.LENGTH_SHORT).show()
                return@launch
            }
            showDownloadDialog(video, mode, list)
        }
    }

    private fun showDownloadDialog(
        video: com.miappvideos.model.PipedVideo,
        mode: String,
        list: List<com.miappvideos.api.innertube.StreamResolver.DownloadOption>,
    ) {
        val labels = list.map { it.label }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (mode == DownloadService.MODE_VIDEO) "Descargar video" else "Descargar música (MP3)")
            .setItems(labels) { _, which ->
                beginDownload(video, mode, list[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun beginDownload(
        video: com.miappvideos.model.PipedVideo,
        mode: String,
        option: com.miappvideos.api.innertube.StreamResolver.DownloadOption,
    ) {
        val videoId = extractVideoId(video.url.orEmpty())
        if (videoId.isEmpty()) {
            Toast.makeText(this, "Video no válido para descargar", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                pendingDownload = Triple(video, option, mode)
                requestPermissions(
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    PERMISSION_STORAGE
                )
                return
            }
        }
        launchDownloadService(videoId, video.title, mode, option)
    }

    private fun launchDownloadService(
        videoId: String,
        title: String,
        mode: String,
        option: com.miappvideos.api.innertube.StreamResolver.DownloadOption,
    ) {
        val intent = Intent(this, DownloadService::class.java).apply {
            putExtra(DownloadService.EXTRA_VIDEO_ID, videoId)
            putExtra(DownloadService.EXTRA_TITLE, title)
            putExtra(DownloadService.EXTRA_MODE, mode)
            putExtra(DownloadService.EXTRA_URL, option.url)
            putExtra(DownloadService.EXTRA_MIME, option.mime)
            putExtra(DownloadService.EXTRA_EXT, option.ext)
            putExtra(DownloadService.EXTRA_LABEL, option.label)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Descargando ${option.label}...", Toast.LENGTH_SHORT).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_STORAGE) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) {
                pendingDownload?.let { (video, mode, option) ->
                    pendingDownload = null
                    beginDownload(video, mode, option)
                }
            } else {
                pendingDownload = null
                Toast.makeText(this, "Permiso de almacenamiento denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showQueueOptions(index: Int) {
        if (index < 0 || index >= videoQueue.size) return
        val options = arrayOf("Reproducir ahora", "Quitar de la cola")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(videoQueue[index].title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> playQueueItem(index)
                    1 -> {
                        videoQueue.removeAt(index)
                        if (currentQueueIndex > index) currentQueueIndex--
                        else if (currentQueueIndex == index) currentQueueIndex = -1
                        refreshQueue()
                    }
                }
            }
            .show()
    }

    private fun showMenuDialog() {
        val dataSaverState = if (DataSaver.isEnabled(this)) "activado" else "desactivado"
        val items = mutableListOf("Cambiar tema", "Ahorro de datos: $dataSaverState")
        if (isSignedIn) items.add("Cerrar sesión")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Opciones")
            .setItems(items.toTypedArray()) { _, which ->
                when (which) {
                    0 -> cycleTheme()
                    1 -> {
                        val newState = !DataSaver.isEnabled(this)
                        DataSaver.setEnabled(this, newState)
                        Toast.makeText(
                            this,
                            if (newState) "Ahorro de datos activado (solo audio)" else "Ahorro de datos desactivado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    2 -> logout()
                }
            }
            .show()
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            playerManager.isPlaying = !playerManager.isPlaying
        }

        btnBackground.setOnClickListener {
            startBackgroundPlayback()
        }

        btnShuffle.setOnClickListener {
            isShuffle = !isShuffle
            updateRepeatShuffleTint()
        }

        btnRepeat.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            updateRepeatShuffleTint()
        }

        btnPrev.setOnClickListener { prevVideo() }
        btnNext.setOnClickListener { nextVideo() }

        btnQuality.setOnClickListener { showQualityDialog() }
        btnLyrics.setOnClickListener { showLyrics() }

        btnPip.setOnClickListener {
            enterPipMode()
        }

        btnSearch.setOnClickListener {
            startActivityForResult(Intent(this, SearchActivity::class.java), RC_SEARCH)
        }

        btnMenu.setOnClickListener {
            showMenuDialog()
        }

        btnCreate.setOnClickListener {
            Toast.makeText(this, "Función de crear aún no disponible", Toast.LENGTH_SHORT).show()
        }

        btnAvatar.setOnClickListener {
            openAccount()
        }

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            when (id) {
                R.id.chipTodo -> loadTrending()
                else -> {
                    savePreferredCategory(categoryQuery(id))
                    searchVideos(categoryQuery(id))
                }
            }
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    savePreferredCategory(query)
                    searchVideos(query)
                    searchLayout.visibility = View.GONE
                    searchVisible = false
                }
                true
            } else false
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener {
            loadTrending { swipeRefresh.isRefreshing = false }
        }
        swipeRefresh.setColorSchemeResources(
            com.google.android.material.R.color.design_default_color_primary,
            android.R.color.holo_green_dark,
            android.R.color.holo_orange_dark
        )
    }

    private fun setupProgress() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatPlaybackTime(
                        if (playerManager.player.duration > 0)
                            (playerManager.player.duration * progress / 1000L)
                        else 0L
                    )
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                seeking = true
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                seeking = false
                val duration = playerManager.player.duration
                if (duration > 0) {
                    playerManager.player.seekTo(sb?.progress?.toLong()?.times(duration)?.div(1000L) ?: 0L)
                }
            }
        })

        lifecycleScope.launch {
            while (true) {
                val duration = playerManager.player.duration
                val position = playerManager.player.currentPosition
                tvCurrentTime.text = formatPlaybackTime(position)
                tvTotalTime.text = formatPlaybackTime(duration)
                if (!seeking && duration > 0) {
                    seekBar.progress = ((position * 1000L) / duration).toInt().coerceIn(0, 1000)
                }
                delay(500)
            }
        }
    }

    private fun formatPlaybackTime(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(java.util.Locale.US, "%d:%02d", m, s)
    }

    private fun categoryQuery(id: Int): String = when (id) {
        R.id.chipTodo -> "música"
        R.id.chipPop -> "pop"
        R.id.chipReggaeton -> "reggaetón"
        R.id.chipSalsa -> "salsa"
        R.id.chipCumbia -> "cumbia"
        R.id.chipRock -> "rock en español"
        R.id.chipBachata -> "bachata"
        R.id.chipBaladas -> "baladas románticas"
        R.id.chipVallenato -> "vallenato"
        R.id.chipElectronica -> "música electrónica"
        R.id.chipCorridos -> "corridos"
        else -> "música"
    }

    private fun loadAvatar() {
        if (currentPhotoUrl != null) {
            btnAvatar.load(currentPhotoUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
            }
        } else {
            btnAvatar.setImageResource(R.drawable.ic_account_circle)
        }
    }

    private fun toggleSearch() {
        searchVisible = !searchVisible
        searchLayout.visibility = if (searchVisible) View.VISIBLE else View.GONE
        if (searchVisible) {
            searchInput.requestFocus()
        }
    }

    private fun loadTrending(onComplete: (() -> Unit)? = null) {
        if (isLoading) {
            onComplete?.invoke()
            return
        }
        isLoading = true
        lifecycleScope.launch {
            val combined = mutableListOf<com.miappvideos.model.PipedVideo>()
            try {
                val innerTubeResults = com.miappvideos.api.innertube.InnerTubeSearch.search("${preferredQuery()} música")
                if (innerTubeResults.isNotEmpty()) {
                    combined.addAll(innerTubeResults.filter { recommendationEngine.isMusicVideo(it) })
                }
            } catch (_: Exception) {}
            try {
                val ytVideos = youTubeManager.getPopularVideos()
                if (ytVideos.isNotEmpty()) {
                    combined.addAll(ytVideos.map { it.toPipedVideo() })
                }
            } catch (_: Exception) {}

            try {
                val result = api.search("${preferredQuery()} música")
                combined.addAll(result.items.filter { recommendationEngine.isMusicVideo(it) })
            } catch (_: Exception) {}

            if (combined.isNotEmpty()) {
                val genre = genreFromQuery()
                val recentAuthors = recentAuthorsList()
                val seen = watchHistory.mapNotNull { extractVideoId(it.url.orEmpty()) }.toSet()
                val ranked = recommendationEngine.rankCandidates(
                    combined, null, genre, recentAuthors, seen, { v -> extractVideoId(v.url.orEmpty()) }, jitter = true
                )
                adapter.updateVideos(ranked.take(30))
                isLoading = false
                onComplete?.invoke()
                return@launch
            }

            val fallbackQueries = listOf("música pop", "música salsa", "música cumbia", "música romántica", "música reggaetón")
            val query = fallbackQueries[refreshIndex % fallbackQueries.size]
            refreshIndex++
            try {
                val result = api.search(query)
                if (result.items.isNotEmpty()) {
                    adapter.updateVideos(result.items.filter { recommendationEngine.isMusicVideo(it) }.take(20))
                    isLoading = false
                    onComplete?.invoke()
                    return@launch
                }
            } catch (_: Exception) {}
            runOnUiThread { Toast.makeText(this@MainActivity, "No se pudieron cargar videos. Verifica que YouTube Data API esté habilitada.", Toast.LENGTH_LONG).show() }
            isLoading = false
            onComplete?.invoke()
        }
    }

    private fun searchVideos(query: String) {
        lifecycleScope.launch {
            val musicQuery = listOfNotNull(query.takeIf { it.isNotBlank() }, "música")
                .joinToString(" ")
            try {
                val innerTubeResults = com.miappvideos.api.innertube.InnerTubeSearch.search(musicQuery)
                if (innerTubeResults.isNotEmpty()) {
                    adapter.updateVideos(innerTubeResults.take(30))
                    return@launch
                }
            } catch (_: Exception) {}
            try {
                val ytVideos = youTubeManager.searchYouTube(musicQuery, musicOnly = true)
                if (ytVideos.isNotEmpty()) {
                    adapter.updateVideos(ytVideos.map { it.toPipedVideo() })
                    return@launch
                }
            } catch (_: Exception) {}

            try {
                val result = api.search(musicQuery)
                adapter.updateVideos(result.items.take(30))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error al buscar", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSubscriptions() {
        if (!isSignedIn) {
            Toast.makeText(this, "Inicia sesión para ver tus suscripciones", Toast.LENGTH_SHORT).show()
            openAccount()
            return
        }
        lifecycleScope.launch {
            val subs = youTubeManager.getSubscriptions()
            if (subs.isEmpty()) {
                Toast.makeText(this@MainActivity, "Sin suscripciones o error al cargar", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val allVideos = mutableListOf<com.miappvideos.model.YouTubeVideo>()
            val channelIds = subs.mapNotNull { it.snippet?.resourceId?.channelId }
            for (channelId in channelIds.take(10)) {
                val videos = youTubeManager.getChannelUploadsVideos(channelId)
                allVideos.addAll(videos.take(3))
            }

            val unique = LinkedHashMap<String, com.miappvideos.model.YouTubeVideo>()
            for (video in allVideos) {
                val id = video.snippet?.resourceId?.videoId ?: continue
                unique[id] = video
            }

            if (unique.isEmpty()) {
                Toast.makeText(this@MainActivity, "No se pudieron cargar videos de tus suscripciones", Toast.LENGTH_SHORT).show()
            } else {
                val pipedVideos = unique.values
                    .sortedByDescending { it.snippet?.publishedAt ?: "" }
                    .map { it.toPipedVideo() }
                adapter.updateVideos(pipedVideos)
                Toast.makeText(this@MainActivity, "${pipedVideos.size} videos de tus suscripciones", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAccount() {
        if (!isSignedIn) {
            val intent = Intent(this, com.miappvideos.auth.LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(R.layout.profile_dialog)
            .create()

        dialog.setOnShowListener {
            val avatar = dialog.findViewById<ImageView>(R.id.profileAvatar)!!
            val name = dialog.findViewById<TextView>(R.id.profileName)!!
            val email = dialog.findViewById<TextView>(R.id.profileEmail)!!
            val playlistRv = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.profilePlaylists)!!
            val historyRv = dialog.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.profileHistory)!!
            val btnLogout = dialog.findViewById<Button>(R.id.btnLogout)!!

            name.text = currentName
            email.text = currentEmail ?: ""
            if (currentPhotoUrl != null) {
                avatar.load(currentPhotoUrl) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            }

            btnLogout.setOnClickListener {
                logout()
                dialog.dismiss()
            }

            lifecycleScope.launch {
                val playlists = youTubeManager.getPlaylists()
                if (playlists.isNotEmpty()) {
                    playlistRv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
                    playlistRv.adapter = PlaylistAdapter(playlists)
                }
            }

            if (watchHistory.isNotEmpty()) {
                historyRv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
                historyRv.adapter = com.miappvideos.adapter.VideoAdapter(
                    watchHistory.take(10),
                    onVideoClick = { video ->
                        dialog.dismiss()
                        playVideo(video)
                    }
                )
            }
        }

        dialog.show()
    }

    private fun logout() {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this,
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build()
        ).signOut()
        isSignedIn = false
        currentEmail = null
        currentName = "Invitado"
        currentPhotoUrl = null
        titleTextView.text = "Bienvenido, Invitado"
        youTubeManager.setAccessToken(null)
        loadAvatar()
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()
    }

    private fun playVideo(video: com.miappvideos.model.PipedVideo) {
        val videoId = extractVideoId(video.url ?: return)
        playerManager.currentVideoId = videoId
        playerManager.currentTitle = video.title
        playerManager.currentThumbnail = video.thumbnail
        currentArtist = video.uploaderName
        titleTextView.text = video.title

        miniTitle.text = video.title
        miniChannel.text = video.uploaderName ?: ""

        isExpanded = true
        miniPlayer.visibility = View.GONE
        fullPlayerContainer.visibility = View.VISIBLE
        playerContainer.visibility = View.VISIBLE
        enterPlaybackScreen()

        if (video.thumbnail != null) {
            miniThumbnail.load(video.thumbnail)
        }

        val index = videoQueue.indexOfFirst { extractVideoId(it.url.orEmpty()) == videoId }
        if (index >= 0) {
            currentQueueIndex = index
            maybeExtendQueue()
        } else {
            videoQueue.clear()
            videoQueue.add(video)
            currentQueueIndex = 0
            loadSimilarVideos(video)
        }

        watchHistory.removeAll { extractVideoId(it.url.orEmpty()) == videoId }
        watchHistory.add(0, video)
        if (watchHistory.size > 50) watchHistory.removeAt(watchHistory.lastIndex)
        saveWatchHistory()

        lifecycleScope.launch {
            val stream = com.miappvideos.api.MusicStreamProvider.getStream(videoId)
            if (stream != null) {
                currentAudioUrl = stream.audioUrl
                currentVideoUrl = stream.videoUrl
                currentVideoQualities = stream.videoQualities

                val videoUrl = if (DataSaver.isEnabled(this@MainActivity)) null else stream.videoUrl
                playerManager.playAudioVideo(stream.audioUrl, videoUrl, video.title)
                updateQualityButton(videoUrl)
                startBackgroundPlayback(showToast = false)
                showMiniPlayer()
            } else {
                Toast.makeText(this@MainActivity, "No se pudo obtener el audio", Toast.LENGTH_SHORT).show()
            }
        }

        preloadAdjacent()
        refreshQueue()
    }

    private fun updateQualityButton(activeVideoUrl: String?) {
        if (activeVideoUrl == null || currentVideoQualities.isEmpty()) {
            btnQuality.visibility = View.GONE
            return
        }
        val match = currentVideoQualities.firstOrNull { it.url == activeVideoUrl }
        btnQuality.text = match?.label ?: currentVideoQualities.firstOrNull()?.label ?: "480p"
        btnQuality.visibility = View.VISIBLE
    }

    private fun showQualityDialog() {
        if (currentVideoQualities.isEmpty()) {
            Toast.makeText(this, "Solo hay una calidad disponible", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = currentVideoQualities.map { it.label }.toTypedArray()
        val checked = currentVideoQualities.indexOfFirst { it.url == currentVideoUrl }

        android.app.AlertDialog.Builder(this)
            .setTitle("Calidad de video")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = currentVideoQualities[which]
                switchVideoQuality(selected)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun switchVideoQuality(selected: com.miappvideos.api.innertube.StreamResolver.VideoQuality) {
        val audio = currentAudioUrl ?: return
        val positionMs = playerManager.player.currentPosition

        Log.d("Quality", "cambiando a ${selected.label} (itag=${selected.itag}) pos=$positionMs")

        // Conservar el estado de reproduccion actual y la posicion
        playerManager.playAudioVideo(audio, selected.url, playerManager.currentTitle, positionMs)
        currentVideoUrl = selected.url
        btnQuality.text = selected.label
    }

    private fun showLyrics() {
        val title = playerManager.currentTitle
        if (title.isBlank()) {
            Toast.makeText(this, "Reproduce una canción primero", Toast.LENGTH_SHORT).show()
            return
        }
        val artist = currentArtist

        val waiting = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Letra")
            .setMessage("Buscando letra...")
            .setCancelable(true)
            .show()

        lifecycleScope.launch {
            val lyrics = com.miappvideos.util.LyricsProvider.fetch(title, artist)
            if (waiting.isShowing) waiting.dismiss()

            val message = when {
                lyrics == null -> "No se encontró letra para este video."
                lyrics.instrumental -> "Esta canción es instrumental (no tiene letra)."
                else -> lyrics.text
            }

            androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle(if (lyrics != null) "${lyrics.artistName} - ${lyrics.trackName}" else title)
                .setMessage(message)
                .setPositiveButton("Cerrar", null)
                .show()
        }
    }

    private fun loadSimilarVideos(video: com.miappvideos.model.PipedVideo) {
        val videoId = extractVideoId(video.url.orEmpty())
        lifecycleScope.launch {
            val seen = (videoQueue.mapNotNull { extractVideoId(it.url.orEmpty()) } +
                    watchHistory.mapNotNull { extractVideoId(it.url.orEmpty()) }).toSet()
            val filtered = recommendationEngine.findSimilarVideos(
                video, seen, genreFromQuery(), recentAuthorsList()
            )
            if (filtered.isNotEmpty() && playerManager.currentVideoId == videoId) {
                videoQueue.addAll(filtered)
                refreshQueue()
                Log.d("Autoplay", "similares cargados: ${filtered.size} para $videoId")
            }
        }
    }

    private fun extractVideoId(url: String): String {
    private fun extractVideoId(url: String): String {
        return when {
            url.contains("watch?v=") -> url.substringAfter("watch?v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            url.length == 11 -> url
            else -> url.takeLast(11)
        }
    }

    private fun loadWatchHistory() {
        val prefs = getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("history_json", null) ?: return
        try {
            val arr = org.json.JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val video = com.miappvideos.model.PipedVideo(
                    url = obj.optString("url").takeIf { it.isNotEmpty() },
                    title = obj.optString("title", "Sin título"),
                    thumbnail = obj.optString("thumbnail").takeIf { it.isNotEmpty() },
                    uploaderName = obj.optString("uploaderName").takeIf { it.isNotEmpty() },
                    uploaderAvatar = obj.optString("uploaderAvatar").takeIf { it.isNotEmpty() },
                    uploadedDate = obj.optString("uploadedDate").takeIf { it.isNotEmpty() },
                    shortDescription = obj.optString("shortDescription").takeIf { it.isNotEmpty() },
                    duration = if (obj.isNull("duration")) null else obj.optLong("duration", 0).takeIf { it > 0 },
                    views = if (obj.isNull("views")) null else obj.optLong("views", 0).takeIf { it > 0 },
                    uploaderVerified = if (obj.isNull("uploaderVerified")) null else obj.optBoolean("uploaderVerified"),
                    channelId = obj.optString("channelId").takeIf { it.isNotEmpty() }
                )
                watchHistory.add(video)
            }
        } catch (_: Exception) {}
    }

    private fun saveWatchHistory() {
        val prefs = getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray()
        for (video in watchHistory) {
            val obj = org.json.JSONObject()
            try {
                obj.put("url", video.url ?: "")
                obj.put("title", video.title)
                obj.put("thumbnail", video.thumbnail ?: "")
                obj.put("uploaderName", video.uploaderName ?: "")
                obj.put("uploaderAvatar", video.uploaderAvatar ?: "")
                obj.put("uploadedDate", video.uploadedDate ?: "")
                obj.put("shortDescription", video.shortDescription ?: "")
                obj.put("channelId", video.channelId ?: "")
                if (video.duration != null) obj.put("duration", video.duration) else obj.put("duration", org.json.JSONObject.NULL)
                if (video.views != null) obj.put("views", video.views) else obj.put("views", org.json.JSONObject.NULL)
                if (video.uploaderVerified != null) obj.put("uploaderVerified", video.uploaderVerified) else obj.put("uploaderVerified", org.json.JSONObject.NULL)
            } catch (_: Exception) {}
            arr.put(obj)
        }
        prefs.edit().putString("history_json", arr.toString()).apply()
    }

    private fun startBackgroundPlayback(showToast: Boolean = true) {
        isBackgroundMode = true
        val intent = Intent(this, PlayerService::class.java).apply {
            putExtra("video_id", playerManager.currentVideoId)
            putExtra("title", playerManager.currentTitle)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        if (showToast) {
            Toast.makeText(this, "Reproduciendo en segundo plano", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
            isInPipMode = true
        } else {
            Toast.makeText(this, "PiP no soportado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePlayPauseIcon() {
        val icon = if (isPlaying)
            R.drawable.ic_pause
        else
            R.drawable.ic_play_arrow
        btnPlayPause.setImageResource(icon)
        miniPlayPause.setImageResource(icon)
    }

    private fun enterPlaybackScreen() {
        isExpanded = true
        miniPlayer.visibility = View.GONE
        fullPlayerContainer.visibility = View.VISIBLE
        playerContainer.visibility = View.VISIBLE
        swipeRefresh.visibility = View.GONE
        chipsRow.visibility = View.GONE
        searchLayout.visibility = View.GONE
        searchVisible = false

        val cs = ConstraintSet()
        cs.clone(rootLayout)
        cs.clear(playerContainer.id, ConstraintSet.TOP)
        cs.connect(playerContainer.id, ConstraintSet.TOP, toolbar.id, ConstraintSet.BOTTOM)
        cs.connect(playerContainer.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        cs.constrainHeight(playerContainer.id, ConstraintSet.MATCH_CONSTRAINT)
        cs.applyTo(rootLayout)
    }

    private fun leavePlaybackScreen() {
        isExpanded = false
        miniPlayer.visibility = View.VISIBLE
        fullPlayerContainer.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE
        chipsRow.visibility = View.VISIBLE

        val cs = ConstraintSet()
        cs.clone(rootLayout)
        cs.clear(playerContainer.id, ConstraintSet.TOP)
        cs.clear(playerContainer.id, ConstraintSet.BOTTOM)
        cs.connect(playerContainer.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        cs.constrainHeight(playerContainer.id, ConstraintSet.WRAP_CONTENT)
        cs.applyTo(rootLayout)
    }

    private fun togglePlayerMode() {
        if (isExpanded) leavePlaybackScreen() else enterPlaybackScreen()
    }

    private fun showMiniPlayer() {
        playerContainer.visibility = View.VISIBLE
        if (isExpanded) enterPlaybackScreen() else leavePlaybackScreen()
    }

    private fun handlePlaybackEnded() {
        Log.d("Autoplay", "handlePlaybackEnded repeat=$repeatMode queueSize=${videoQueue.size} index=$currentQueueIndex")
        when (repeatMode) {
            2 -> {
                playerManager.player.seekTo(0)
                playerManager.isPlaying = true
            }
            1 -> nextVideo()
            else -> {
                if (currentQueueIndex < videoQueue.size - 1) {
                    nextVideo()
                } else {
                    autoplayNext()
                }
            }
        }
    }

    private fun maybeExtendQueue() {
        if (extendingQueue) return
        val remaining = videoQueue.size - currentQueueIndex
        if (remaining > 5) return
        extendingQueue = true
        Log.d("Autoplay", "extendiendo cola: quedan $remaining")
        lifecycleScope.launch {
            try {
                val last = videoQueue.lastOrNull()
                val seen = (videoQueue.mapNotNull { extractVideoId(it.url.orEmpty()) } +
                        watchHistory.mapNotNull { extractVideoId(it.url.orEmpty()) }).toSet()
                val fresh = recommendationEngine.searchNewVideos(
                    query = preferredQuery(),
                    seed = last,
                    seen = seen,
                    genre = genreFromQuery(),
                    recentAuthors = recentAuthorsList(),
                    count = 10
                )
                if (fresh.isNotEmpty()) {
                    videoQueue.addAll(fresh)
                    refreshQueue()
                    Log.d("Autoplay", "cola extendida: +${fresh.size} nuevos, total=${videoQueue.size}")
                } else {
                    Log.d("Autoplay", "no hay videos nuevos para extender")
                }
            } finally {
                extendingQueue = false
            }
        }
    }

    private suspend fun searchNewVideos(count: Int): List<com.miappvideos.model.PipedVideo> {
        val last = videoQueue.lastOrNull()
        val seen = (videoQueue.mapNotNull { extractVideoId(it.url.orEmpty()) } +
                watchHistory.mapNotNull { extractVideoId(it.url.orEmpty()) }).toSet()
        val fresh = mutableListOf<com.miappvideos.model.PipedVideo>()
        if (last != null) {
            fresh.addAll(findSimilarVideos(last))
        }
        if (fresh.size < count) {
            val fallback = listOfNotNull(
                preferredQuery().takeIf { it.isNotBlank() },
                "música"
            ).first()
            val fallbackItems = mutableListOf<com.miappvideos.model.PipedVideo>()
            try {
                val ytVideos = youTubeManager.searchYouTube(fallback, musicOnly = true)
                if (ytVideos.isNotEmpty()) {
                    fallbackItems.addAll(ytVideos.map { it.toPipedVideo() })
                }
            } catch (_: Exception) {}
            if (fallbackItems.isEmpty()) {
                try {
                    fallbackItems.addAll(api.search(fallback).items)
                } catch (e: Exception) {
                    Log.e("Autoplay", "error fallback extension", e)
                }
            }
            fresh.addAll(fallbackItems.filter {
                extractVideoId(it.url.orEmpty()).let { id -> id.isNotEmpty() && id !in seen } &&
                        isMusicVideo(it)
            }.filter { v ->
                fresh.none { f -> sameSong(f.title.orEmpty(), v.title.orEmpty()) }
            }.take(count))
        }
        val currentId = extractVideoId(videoQueue.getOrNull(currentQueueIndex)?.url.orEmpty())
        val seed = videoQueue.getOrNull(currentQueueIndex) ?: last
        val genre = genreFromQuery()
        val recentAuthors = recentAuthorsList()
        val seenIds = seen + currentId
        return RecommendationEngine.rankCandidates(
            fresh, seed, genre, recentAuthors, seenIds, { v -> extractVideoId(v.url.orEmpty()) }
        ).take(count)
    }

    private fun autoplayNext() {
        Log.d("Autoplay", "autoplayNext query=${preferredQuery()}")
        lifecycleScope.launch {
            try {
                val last = videoQueue.lastOrNull()
                val seen = (videoQueue.mapNotNull { extractVideoId(it.url.orEmpty()) } +
                        watchHistory.mapNotNull { extractVideoId(it.url.orEmpty()) }).toSet()
                val fresh = recommendationEngine.searchNewVideos(
                    query = preferredQuery(),
                    seed = last,
                    seen = seen,
                    genre = genreFromQuery(),
                    recentAuthors = recentAuthorsList(),
                    count = 10
                )
                if (fresh.isNotEmpty()) {
                    videoQueue.addAll(fresh)
                    refreshQueue()
                    currentQueueIndex = videoQueue.size - fresh.size
                    Log.d("Autoplay", "autoplay: +${fresh.size} nuevos, total=${videoQueue.size}")
                    playVideo(videoQueue[currentQueueIndex])
                } else {
                    Log.d("Autoplay", "no candidate found")
                    playerManager.isPlaying = false
                }
            } catch (e: Exception) {
                Log.e("Autoplay", "error en autoplay", e)
                playerManager.isPlaying = false
            }
        }
    }

    private fun preferredQuery(): String {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        return prefs.getString("preferred_category", null) ?: autoplayQueries.random()
    }

    private fun savePreferredCategory(category: String) {
        getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            .edit().putString("preferred_category", category).apply()
    }

    private fun genreFromQuery(): String? {
        val q = preferredQuery().lowercase()
        return RecommendationEngine.GENRES.firstOrNull { (key, kws) ->
            kws.any { it in q } || key in q
        }?.key
    }

    private fun recentAuthorsList(): List<String> =
        watchHistory.take(6).mapNotNull { it.uploaderName }

    private fun playQueueItem(index: Int) {
        if (index < 0 || index >= videoQueue.size) return
        currentQueueIndex = index
        playVideo(videoQueue[index])
    }

    private fun prevVideo() {
        if (videoQueue.isEmpty()) return
        val newIndex = if (currentQueueIndex > 0) currentQueueIndex - 1 else videoQueue.size - 1
        playQueueItem(newIndex)
    }

    private fun nextVideo() {
        if (videoQueue.isEmpty()) return
        val newIndex = if (isShuffle && videoQueue.size > 1) {
            var random = (0 until videoQueue.size).random()
            while (random == currentQueueIndex) random = (0 until videoQueue.size).random()
            random
        } else if (currentQueueIndex < videoQueue.size - 1) {
            currentQueueIndex + 1
        } else {
            0
        }
        playQueueItem(newIndex)
    }

    private fun updateRepeatShuffleTint() {
        val activeTint = android.content.res.ColorStateList.valueOf(
            themeColor(com.google.android.material.R.attr.colorPrimary)
        )
        val inactiveTint = android.content.res.ColorStateList.valueOf(
            themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        btnShuffle.imageTintList = if (isShuffle) activeTint else inactiveTint
        btnRepeat.imageTintList = when (repeatMode) {
            0 -> inactiveTint
            else -> activeTint
        }
        btnShuffle.alpha = if (isShuffle) 1f else 0.5f
        btnRepeat.alpha = if (repeatMode == 0) 0.5f else 1f
    }

    private fun themeColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }

    private fun preloadAdjacent() {
        val next = currentQueueIndex + 1
        val prev = currentQueueIndex - 1
        val dataSaver = DataSaver.isEnabled(this)
        lifecycleScope.launch {
            if (next < videoQueue.size) {
                val nextId = extractVideoId(videoQueue[next].url.orEmpty())
                if (nextId.isNotEmpty()) com.miappvideos.api.MusicStreamProvider.preload(nextId)
            }
            if (!dataSaver && prev >= 0) {
                val prevId = extractVideoId(videoQueue[prev].url.orEmpty())
                if (prevId.isNotEmpty()) com.miappvideos.api.MusicStreamProvider.preload(prevId)
            }
        }
    }

    private fun updatePipButtonVisibility() {
        btnPip.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            View.VISIBLE else View.GONE
    }

    private fun cycleTheme() {
        val prefs = getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        val currentMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val newMode = when (currentMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        prefs.edit().putInt("theme_mode", newMode).apply()
        AppCompatDelegate.setDefaultNightMode(newMode)
        recreate()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isInPipMode) {
            val aspectRatio = Rational(16, 9)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
            isInPipMode = true
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            recyclerVideos.visibility = View.GONE
        } else {
            recyclerVideos.visibility = View.VISIBLE
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isInPipMode && !isBackgroundMode) {
            if (!playerManager.player.isPlaying) {
                playerManager.player.pause()
            }
        }
    }

    override fun onBackPressed() {
        if (isExpanded) {
            leavePlaybackScreen()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isBackgroundMode) {
            stopService(Intent(this, PlayerService::class.java))
            playerManager.release()
        }
    }
}

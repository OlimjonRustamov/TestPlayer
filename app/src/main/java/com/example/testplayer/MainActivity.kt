package com.example.testplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.example.testplayer.databinding.ActivityMainBinding
import kotlin.math.abs


@SuppressLint("UnsafeOptInUsageError")
class MainActivity : AppCompatActivity() {

    private val url = "https://ftp18c1.cinerama.uz/hls/converted/20240629/movies/b4d00997650756c224ec8428a25f1bc6.mp4/playlist.m3u8?token=MLEpPA_3COSK3R2e8gf6sA&expires=1729076164&ip=195.158.4.23"

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val player by lazy {
        ExoPlayer.Builder(this).build()
    }

    private lateinit var audioManager: AudioManager

    var moving: Boolean = false
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val enableClickRunnable = Runnable { moving = false }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars.
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        window.setFlags(FLAG_LAYOUT_NO_LIMITS, FLAG_LAYOUT_NO_LIMITS)
        binding.playerView.setOnApplyWindowInsetsListener { v, insets ->
            WindowInsetsCompat.toWindowInsetsCompat(insets, v)
                // Actual insets are inexplicably wrong when a SearchView is expanded.
                .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() + WindowInsetsCompat.Type.displayCutout())
                .apply {
                    val r = binding.playerView.findViewById<ConstraintLayout>(R.id.controllersRoot)
                    r.setPadding(left, top, right, bottom)
                    Log.d("TTTT_INSETS", "onCreate: $this")
                }
            insets
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val interval: Long = 200
        var onRightClickedTime: Long = 0L
        var onLeftClickedTime: Long = 0L
        var isLeftDoubleClicked = false
        var isRightDoubleClicked = false

        binding.playerView.findViewById<View>(R.id.leftView).setOnClickListener {
            if (moving) return@setOnClickListener
            val currentTime = System.currentTimeMillis()

            if (currentTime - onLeftClickedTime < interval) {
                isLeftDoubleClicked = true
                Toast.makeText(this, "Left Double Click", Toast.LENGTH_SHORT).show()
            } else {
                isLeftDoubleClicked = false
                it.postDelayed({
                    if (!isLeftDoubleClicked) {
                        binding.playerView.hideController()
                    }
                }, interval)
            }
            onLeftClickedTime = currentTime
        }

        binding.playerView.findViewById<View>(R.id.rightView).setOnClickListener {
            if (moving) return@setOnClickListener
            val currentTime = System.currentTimeMillis()

            if (currentTime - onRightClickedTime < interval) {
                isRightDoubleClicked = true
                Toast.makeText(this, "Right Double Click", Toast.LENGTH_SHORT).show()
            } else {
                isRightDoubleClicked = false
                it.postDelayed({
                    if (!isRightDoubleClicked) {
                        binding.playerView.hideController()
                    }
                }, interval)
            }
            onRightClickedTime = currentTime
        }

        var initialLeftY: Float = 0f
        var volumeBeforeSwipe = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume  = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.playerView.findViewById<View>(R.id.leftView).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialLeftY = event.y
                    volumeBeforeSwipe = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    return@setOnTouchListener false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY: Float = event.y - initialLeftY
                    if (abs(deltaY) > 50) {  // Swipe threshold
                        moving = true
                        handler.removeCallbacks(enableClickRunnable)
                        if (deltaY > 0) {
                            val volume = volumeBeforeSwipe - 1
                            volumeBeforeSwipe = volume
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
                        } else {
                            val volume = volumeBeforeSwipe + 1
                            volumeBeforeSwipe = volume
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
                        }
                        initialLeftY = event.y
                        return@setOnTouchListener false
                    }
                }
                MotionEvent.ACTION_UP -> handler.postDelayed(enableClickRunnable, 200)
            }
            false
        }

        val canWrite = Settings.System.canWrite(this)
        if (!canWrite) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.setData(Uri.parse("package:" + this.packageName))
            startActivity(intent)
        }
        val maxBrightness = 100
        var currentBrightness = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS, -1
        )
        val layout: WindowManager.LayoutParams = window.attributes
        layout.screenBrightness = currentBrightness.toFloat() / 100
        window.setAttributes(layout)
        binding.playerView.findViewById<View>(R.id.rightView).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialLeftY = event.y
                    volumeBeforeSwipe = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    return@setOnTouchListener false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY: Float = event.y - initialLeftY
                    if (abs(deltaY) > 3) {  // Swipe threshold
                        moving = true
                        handler.removeCallbacks(enableClickRunnable)

                        var finalBrightness =
                            if (deltaY > 0) currentBrightness - 1 else currentBrightness + 1
                        if (finalBrightness < 0) finalBrightness = 0
                        else if (finalBrightness > maxBrightness) finalBrightness = maxBrightness
                        currentBrightness = finalBrightness
                        layout.screenBrightness = finalBrightness.toFloat() / 100
                        window.setAttributes(layout)
                        Log.d("TTTT_INSETS", "onCreate: $finalBrightness")
                        initialLeftY = event.y
                        return@setOnTouchListener false
                    }
                }
                MotionEvent.ACTION_UP -> handler.postDelayed(enableClickRunnable, 200)
            }
            false
        }


        binding.playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener {
            if (it == View.VISIBLE) {
                onLeftClickedTime = System.currentTimeMillis()
                onRightClickedTime = System.currentTimeMillis()
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        })

        binding.playerView.findViewById<ImageView>(R.id.exo_play_pause)
            .setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                            .start()
                    }

                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                        v.performClick()
                    }
                }
                true
            }



        binding.playerView.player = player
        val mediaItem = MediaItem.fromUri(url)

        val dataSourceFactory = DefaultHttpDataSource.Factory()
        val hlsMediaSource =
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))

        player.setMediaSource(hlsMediaSource)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

    }
}

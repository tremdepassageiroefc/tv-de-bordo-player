package br.tvbordo.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Tela única do player: toca o canal multicast em tela cheia e se recupera sozinho.
 *
 * - Sem sinal ao abrir: mostra "Aguardando sinal" e tenta de novo a cada [Prefs.timeoutS] s.
 * - Vídeo parou (servidor reiniciou, cabo, switch): reinicia a conexão automaticamente.
 * - Botão Voltar é ignorado (modo quiosque).
 * - OK/Enter 5 vezes em até 3 s: abre as configurações.
 */
class PlayerActivity : Activity() {

    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var overlay: View
    private lateinit var status: TextView
    private lateinit var info: TextView

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private var active = false          // activity visível e deve estar tocando
    private var receiving = false       // vídeo chegando de fato
    private var startedAt = 0L
    private var lastProgressAt = 0L
    private var lastTime = -1L
    private var attempts = 0
    private var restartPending = false

    private var okCount = 0
    private var okFirstAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = Prefs(this)
        videoLayout = findViewById(R.id.video_layout)
        overlay = findViewById(R.id.overlay)
        status = findViewById(R.id.status)
        info = findViewById(R.id.info)
        acquireMulticastLock()
    }

    override fun onStart() {
        super.onStart()
        active = true
        attempts = 0
        restartPending = false
        hideSystemUi()
        startPlayback()
    }

    override fun onStop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        multicastLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    // ------------------------------------------------------------------ reprodução

    private fun startPlayback() {
        if (!active) return
        stopPlayback()
        val url = prefs.url
        val cache = prefs.cacheMs
        try {
            val vlc = LibVLC(
                this, arrayListOf(
                    "--network-caching=$cache",
                    "--live-caching=$cache",
                    "--clock-jitter=0",
                    "--drop-late-frames",
                    "--skip-frames",
                    "--no-sub-autodetect-file",
                    "--no-stats"
                )
            )
            val mp = MediaPlayer(vlc)
            mp.attachViews(videoLayout, null, false, false)
            mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
            mp.setEventListener { ev -> onVlcEvent(ev) }

            val media = Media(vlc, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=$cache")
            mp.media = media
            media.release()
            mp.play()

            libVlc = vlc
            player = mp
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar o player", e)
        }

        attempts++
        receiving = false
        lastTime = -1L
        startedAt = SystemClock.elapsedRealtime()
        lastProgressAt = startedAt
        showWaiting()
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, WATCHDOG_MS)
    }

    private fun stopPlayback() {
        handler.removeCallbacks(watchdog)
        player?.let { mp ->
            try {
                mp.setEventListener(null)
                mp.stop()
                mp.detachViews()
                mp.release()
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao parar: ${e.message}")
            }
        }
        player = null
        libVlc?.let { try { it.release() } catch (_: Exception) { } }
        libVlc = null
    }

    private fun restart(motivo: String) {
        if (!active || restartPending) return // parado ou reinício já agendado
        restartPending = true
        Log.i(TAG, "Reiniciando conexão: $motivo")
        stopPlayback()
        receiving = false
        showWaiting(motivo)
        handler.postDelayed({
            restartPending = false
            startPlayback()
        }, RESTART_DELAY_MS)
    }

    private fun onVlcEvent(ev: MediaPlayer.Event) {
        when (ev.type) {
            MediaPlayer.Event.TimeChanged -> markProgress(ev.timeChanged)
            MediaPlayer.Event.Vout -> if (ev.voutCount > 0) markProgress(null)
            // Posta no handler: não liberar o player de dentro do próprio callback
            MediaPlayer.Event.EncounteredError -> handler.post { restart("erro na reprodução") }
            MediaPlayer.Event.EndReached -> handler.post { restart("transmissão encerrada") }
        }
    }

    private fun markProgress(time: Long?) {
        if (time != null) {
            if (time == lastTime) return
            lastTime = time
        }
        lastProgressAt = SystemClock.elapsedRealtime()
        if (!receiving) {
            receiving = true
            attempts = 0
            overlay.visibility = View.GONE
        }
    }

    /** Confere a cada 2 s se o vídeo está andando; se não, reinicia. */
    private val watchdog = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            val limite = prefs.timeoutS * 1000L

            player?.let { mp -> if (mp.isPlaying && mp.time != lastTime && mp.time > 0) markProgress(mp.time) }

            when {
                !receiving && now - startedAt > limite -> { restart("sem sinal"); return }
                receiving && now - lastProgressAt > limite -> { restart("o vídeo parou"); return }
            }
            if (!receiving) showWaiting()
            handler.postDelayed(this, WATCHDOG_MS)
        }
    }

    // ------------------------------------------------------------------ tela

    private fun showWaiting(motivo: String? = null) {
        overlay.visibility = View.VISIBLE
        val titulo = if (prefs.label.isNotEmpty()) "${prefs.label}\n" else ""
        val linha = when {
            motivo != null -> "Reconectando ($motivo)…"
            attempts > 1 -> "Aguardando sinal… (tentativa $attempts)"
            else -> "Aguardando sinal…"
        }
        status.text = "$titulo$linha"
        info.text = "${prefs.url}\nIP: ${NetInfo.summary()}\nOK 5x: configurações   ·   v${BuildConfigCompat.version(this)}"
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun acquireMulticastLock() {
        // No cabo não é obrigatório, mas algumas boxes filtram multicast sem isto.
        try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("tvbordo")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock indisponível: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ controle remoto

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> return true // quiosque: ignora
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.repeatCount == 0) countOk()
                return true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> {
                openSettings(); return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun countOk() {
        val now = SystemClock.elapsedRealtime()
        if (now - okFirstAt > 3000) {
            okFirstAt = now
            okCount = 0
        }
        okCount++
        if (okCount >= 5) {
            okCount = 0
            openSettings()
        }
    }

    @Suppress("DEPRECATION")
    private fun openSettings() {
        startActivityForResult(Intent(this, SettingsActivity::class.java), REQ_SETTINGS)
    }

    @Deprecated("Activity simples, sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SETTINGS && resultCode == SettingsActivity.RESULT_EXIT) {
            finishAndRemoveTask()
        }
        // Ao voltar das configurações, onStart() reinicia a reprodução com os valores novos.
    }

    companion object {
        private const val TAG = "TVBordo"
        private const val WATCHDOG_MS = 2000L
        private const val RESTART_DELAY_MS = 2000L
        private const val REQ_SETTINGS = 10
    }
}

/** Versão do app sem depender de BuildConfig (desligado por padrão no AGP 8). */
object BuildConfigCompat {
    fun version(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}

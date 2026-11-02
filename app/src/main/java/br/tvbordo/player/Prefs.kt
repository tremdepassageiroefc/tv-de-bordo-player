package br.tvbordo.player

import android.content.Context

/** Configurações do player, salvas na própria box. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tvbordo", Context.MODE_PRIVATE)

    var url: String
        get() = sp.getString(K_URL, DEF_URL) ?: DEF_URL
        set(v) = sp.edit().putString(K_URL, v.trim()).apply()

    /** Buffer de rede em ms (equivale ao "cache de rede" do VLC). */
    var cacheMs: Int
        get() = sp.getInt(K_CACHE, DEF_CACHE)
        set(v) = sp.edit().putInt(K_CACHE, v.coerceIn(200, 10000)).apply()

    /** Segundos sem receber vídeo até reiniciar a conexão. */
    var timeoutS: Int
        get() = sp.getInt(K_TIMEOUT, DEF_TIMEOUT)
        set(v) = sp.edit().putInt(K_TIMEOUT, v.coerceIn(5, 120)).apply()

    /** Abrir o player sozinho quando a box liga. */
    var autostart: Boolean
        get() = sp.getBoolean(K_AUTOSTART, true)
        set(v) = sp.edit().putBoolean(K_AUTOSTART, v).apply()

    /** Nome exibido na tela de "aguardando sinal" (ex.: "Carro 3 - TV 1"). */
    var label: String
        get() = sp.getString(K_LABEL, "") ?: ""
        set(v) = sp.edit().putString(K_LABEL, v.trim()).apply()

    fun reset() = sp.edit().clear().apply()

    companion object {
        const val DEF_URL = "udp://@239.10.10.1:5000"
        const val DEF_CACHE = 1000
        const val DEF_TIMEOUT = 15
        private const val K_URL = "url"
        private const val K_CACHE = "cache_ms"
        private const val K_TIMEOUT = "timeout_s"
        private const val K_AUTOSTART = "autostart"
        private const val K_LABEL = "label"
    }
}

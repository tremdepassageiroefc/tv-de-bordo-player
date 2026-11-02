package br.tvbordo.player

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/** Configurações (abertas com OK 5x no player). Navegável pelo controle remoto. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var url: EditText
    private lateinit var label: EditText
    private lateinit var cache: EditText
    private lateinit var timeout: EditText
    private lateinit var autostart: CheckBox
    private lateinit var homeMode: CheckBox
    private lateinit var info: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        url = findViewById(R.id.edit_url)
        label = findViewById(R.id.edit_label)
        cache = findViewById(R.id.edit_cache)
        timeout = findViewById(R.id.edit_timeout)
        autostart = findViewById(R.id.check_autostart)
        homeMode = findViewById(R.id.check_home)
        info = findViewById(R.id.text_info)

        load()

        findViewById<Button>(R.id.btn_save).setOnClickListener { save(); finish() }
        findViewById<Button>(R.id.btn_cancel).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_defaults).setOnClickListener {
            prefs.reset(); load(); toast("Valores padrão restaurados (salve para aplicar)")
        }
        findViewById<Button>(R.id.btn_overlay).setOnClickListener { openOverlayPermission() }
        findViewById<Button>(R.id.btn_exit).setOnClickListener {
            setResult(RESULT_EXIT); finish()
        }
        url.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshInfo()
    }

    private fun load() {
        url.setText(prefs.url)
        label.setText(prefs.label)
        cache.setText(prefs.cacheMs.toString())
        timeout.setText(prefs.timeoutS.toString())
        autostart.isChecked = prefs.autostart
        homeMode.isChecked = isHomeEnabled()
    }

    private fun save() {
        val u = url.text.toString().trim()
        prefs.url = if (u.isEmpty()) Prefs.DEF_URL else u
        prefs.label = label.text.toString()
        prefs.cacheMs = cache.text.toString().toIntOrNull() ?: Prefs.DEF_CACHE
        prefs.timeoutS = timeout.text.toString().toIntOrNull() ?: Prefs.DEF_TIMEOUT
        prefs.autostart = autostart.isChecked
        setHomeEnabled(homeMode.isChecked)
        toast("Salvo")
    }

    private fun refreshInfo() {
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) "concedida" else "NÃO concedida"
        } else "não necessária"
        val precisa = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        info.text = buildString {
            append("IP: ${NetInfo.summary()}\n")
            append("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})  ·  ")
            append("${Build.MANUFACTURER} ${Build.MODEL}  ·  app v${BuildConfigCompat.version(this@SettingsActivity)}\n")
            append("Permissão 'sobrepor a outros apps': $overlay")
            if (precisa && overlay != "concedida") {
                append("\n→ Sem ela, no Android 10+ o app NÃO abre sozinho ao ligar. ")
                append("Use o botão abaixo ou, pelo PC: adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow")
            }
        }
    }

    private fun openOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            toast("Não é necessária nesta versão do Android"); return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: Exception) {
            // Muitas versões do Android TV não têm essa tela
            toast("Tela não disponível nesta box. Use o comando ADB mostrado nas informações.")
        }
    }

    private val homeAlias get() = ComponentName(this, "$packageName.HomeAlias")

    private fun isHomeEnabled(): Boolean =
        packageManager.getComponentEnabledSetting(homeAlias) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    private fun setHomeEnabled(on: Boolean) {
        if (on == isHomeEnabled()) return
        packageManager.setComponentEnabledSetting(
            homeAlias,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        if (on) toast("Modo tela inicial ligado: aperte HOME e escolha TV de Bordo como padrão, se perguntado")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val RESULT_EXIT = 77
    }
}

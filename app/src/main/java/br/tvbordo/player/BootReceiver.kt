package br.tvbordo.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Abre o player quando a box termina de ligar (ou quando o app é atualizado). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val acoes = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        if (intent.action !in acoes) return
        if (!Prefs(context).autostart) return
        try {
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (e: Exception) {
            // Android 10+ sem a permissão "sobrepor a outros apps": ver README
            Log.w("TVBordo", "Não foi possível abrir no boot: ${e.message}")
        }
    }
}

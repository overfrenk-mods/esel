// ---------- CODICE AUTOSTART 3.1.0 "KICKSTART" ----------
package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;
import esel.esel.esel.util.WakeLockHelper;

public class AutostartReceiver extends BroadcastReceiver {

    private static final String TAG = "AutostartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Verifica di sicurezza sull'azione
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        EselLog.LogW(TAG, "⚡ Riavvio dispositivo (BOOT) rilevato.");

        // 1. WAKELOCK DI SUPPORTO (CRUCIALE PER SAMSUNG)
        // Durante il boot la CPU è intasata. Acquisiamo un lock di 5 secondi
        // per dare al Service il tempo fisico di avviarsi e promuoversi a Foreground.
        WakeLockHelper.acquire(context, 5000);

        try {
            boolean isAppUnlocked = SP.getBoolean("is_app_unlocked", false);
            boolean wasServiceEnabledByUser = SP.getBoolean("enable_service", true);

            if (isAppUnlocked && wasServiceEnabledByUser) {
                EselLog.LogI(TAG, "Check Boot OK. Avvio DataMonitorService...");

                Intent serviceIntent = new Intent(context, DataMonitorService.class);

                // Usiamo startForegroundService per garantire che Android non uccida il servizio
                // dopo 5 secondi considerandolo background illegale.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                EselLog.LogW(TAG, "Boot ignorato. App sbloccata: " + isAppUnlocked + ", Servizio attivo: " + wasServiceEnabledByUser);
            }

        } catch (Exception e) {
            // Su Android 14+, in rari casi di "Background Start Restriction", questo potrebbe fallire.
            // Logghiamo l'errore per non far crashare l'intero processo di boot.
            EselLog.LogE(TAG, "ERRORE CRITICO AL BOOT: " + e.getMessage());
        } finally {
            // Il WakeLock verrà rilasciato automaticamente dal timeout (5s) o dal metodo release
            // ma per sicurezza lo lasciamo gestire al timeout del Helper.
        }
    }
}
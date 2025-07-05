// File: esel/esel/esel/services/StabilityService.java
package esel.esel.esel.services;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import esel.esel.esel.util.EselLog;

/**
 * Questo è un servizio di accessibilità "fittizio".
 * Il suo unico scopo è dare alla nostra app una priorità altissima in memoria
 * per impedire al sistema operativo di terminarla.
 * Non legge, non scrive e non osserva nessuna azione dell'utente.
 */
public class StabilityService extends AccessibilityService {

    private static final String TAG = "StabilityService";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Non facciamo assolutamente nulla con gli eventi.
    }

    @Override
    public void onInterrupt() {
        // Non facciamo nulla se il servizio viene interrotto.
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        EselLog.LogW(TAG, "Servizio di Stabilità CONNESSO. L'app ora ha la massima priorità.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EselLog.LogW(TAG, "Servizio di Stabilità DISTRUTTO.");
    }
}
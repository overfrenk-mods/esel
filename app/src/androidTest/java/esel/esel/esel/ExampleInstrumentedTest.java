package esel.esel.esel; // Assicurati che questo package sia corretto

import android.content.Context;
// ********************************************************************************
// IMPORT AGGIORNATI AD ANDROIDX PER I TEST
// ********************************************************************************
import androidx.test.platform.app.InstrumentationRegistry; // Nuova posizione per InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4; // Runner di test AndroidX

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see Testing documentation
 */
@RunWith(AndroidJUnit4.class) // Usa il runner di test AndroidX
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("esel.esel.esel", appContext.getPackageName());
    }
}
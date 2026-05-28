// Wrapper Java attorno a una libreria nativa C++ (JNI).
// Carica licensechecker.dll/so all'avvio con System.loadLibrary; se manca, fallisce in modo sicuro (NATIVE_LOADED=false) rifiutando qualsiasi licenza.
// Il metodo native checkLicense(key) delega tutto al codice C++.

package ch.supsi.dti.backend.license;

public class LicenseChecker {

    private static final boolean NATIVE_LOADED;
    static {
        // 1. Provo a caricare la libreria nativa (licensechecker.dll / .so / .dylib) dal java.library.path.
        // 2. Se manca, NATIVE_LOADED resta false e il check rifiuta sempre — fail-closed, mai aperto per default.
        boolean loaded = false;
        try {
            System.loadLibrary("licensechecker");
            loaded = true;
        } catch (UnsatisfiedLinkError t) {
            System.err.println("LicenseChecker: native license library not loaded ("
                    + t.getMessage() + "). License validation will fail closed.");
        }
        NATIVE_LOADED = loaded;
    }

    private native boolean checkLicense(String key);

    public boolean verifyLicense(String key) {
        // 1. Guard: senza libreria nativa nessuna chiave è valida.
        if (!NATIVE_LOADED) {
            return false;
        }
        // 2. Delega al codice C++ (vede il formato XXXXX-XXXXX-XXXXX-XXXXX e cerca certe lettere nel codice).
        return checkLicense(key);
    }

    public static boolean isNativeAvailable() {
        return NATIVE_LOADED;
    }
}

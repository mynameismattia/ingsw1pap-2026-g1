package ch.supsi.dti.backend.license;

public class LicenseChecker {

    private static final boolean NATIVE_LOADED;
    static {
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

    /**
     * Native JNI binding. Kept private (and with the original name) so the C
     * symbol {@code Java_ch_supsi_dti_backend_license_LicenseChecker_checkLicense}
     * does not need to be renamed. Callers MUST go through {@link #verifyLicense}.
     */
    private native boolean checkLicense(String key);

    /**
     * Safe entry point used by the application. Fails closed if the native
     * library could not be loaded: returns {@code false} so the caller treats
     * the licence as invalid rather than crashing on an UnsatisfiedLinkError.
     */
    public boolean verifyLicense(String key) {
        if (!NATIVE_LOADED) {
            return false;
        }
        return checkLicense(key);
    }

    /** Reserved for future UX (e.g. Issue #23 demo-mode banner). */
    public static boolean isNativeAvailable() {
        return NATIVE_LOADED;
    }
}

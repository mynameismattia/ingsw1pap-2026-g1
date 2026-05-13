package ch.supsi.dti.backend.license;

public class LicenseChecker {

    public native boolean checkLicense(String key);

    static {
        System.loadLibrary("licensechecker");
    }

}

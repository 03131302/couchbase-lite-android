package com.couchbase.lite.android;

public class SQLiteJsonCollator {
    private static native void nativeRegister(Object database, String databaseClassName, int sdkVersion);

    public static void register(Object database, String databaseClassName, int sdkVersion) {
        nativeRegister(database, databaseClassName, sdkVersion);
    }

    public static native int testCollateJSON(int mode, int len1, String string1, int len2, String string2);
    public static native char testEscape(String source);
    public static native int testDigitToInt(int digit);

    // Sets the root directory to search for the ICU data file
    public static native void setICURoot(String path);
    public static native void setLocale(String locale);
    public static native void releaseICU();

    /**
     * Convenience wrapper around testCollateJSON which calculates lengths based on string lengths
     * of params.
     */
    public static int testCollateJSONWrapper(int mode, String string1, String string2) {
        return testCollateJSON(mode, string1.length(), string1, string2.length(), string2);
    }

    static {
        System.loadLibrary("SQLiteJsonCollator");
    }
}

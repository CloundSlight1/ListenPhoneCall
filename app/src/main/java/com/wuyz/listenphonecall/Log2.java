package com.wuyz.listenphonecall;

import android.util.Log;

public class Log2 {

    private static final boolean DEBUG = true;
    private static final String TAG = "listenphonecall";

    public static void v(String className, String format, Object... args) {
        if (DEBUG)
            Log.v(TAG, className + ", " + String.format(format, args));
    }

    public static void i(String className, String format, Object... args) {
        if (DEBUG)
            Log.i(TAG, className + ", " + String.format(format, args));
    }

    public static void d(String className, String format, Object... args) {
        if (DEBUG)
            Log.d(TAG, className + ", " + String.format(format, args));
    }

    public static void w(String className, String format, Object... args) {
        if (DEBUG)
            Log.w(TAG, className + ", " + String.format(format, args));
    }

    public static void e(String className, String msg, Throwable tr) {
        if (DEBUG)
            Log.e(TAG, className + ", " + msg, tr);
    }

    public static void e(String className, String msg) {
        e(className, msg, null);
    }

    public static void e(String className, Throwable tr) {
        e(className, "", tr);
    }
}

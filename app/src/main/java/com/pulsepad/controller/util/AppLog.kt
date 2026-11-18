package com.pulsepad.controller.util

import android.util.Log

/**
 * Thin wrapper over [Log] so diagnostic logging goes through one place and can be
 * silenced in release builds if needed. Keep tags short and stable: they are used
 * to filter `adb logcat` output during field debugging.
 */
object AppLog {

    const val TAG_ROOT = "PulsePad"

    fun d(tag: String, message: String) {
        Log.d("$TAG_ROOT/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$TAG_ROOT/$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.w("$TAG_ROOT/$tag", message)
        } else {
            Log.w("$TAG_ROOT/$tag", message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable == null) {
            Log.e("$TAG_ROOT/$tag", message)
        } else {
            Log.e("$TAG_ROOT/$tag", message, throwable)
        }
    }
}

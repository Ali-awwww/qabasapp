package com.qabas.app

import android.util.Log
import androidx.compose.ui.graphics.Color

/**
 * Logging helper (Android Log wrapper).
 * Used across the app for debug / info / warn / error.
 */
object DevLog {
    fun d(tag: String, msg: String) = Log.d(tag, msg)
    fun i(tag: String, msg: String) = Log.i(tag, msg)
    fun w(tag: String, msg: String) = Log.w(tag, msg)
    fun e(tag: String, msg: String) = Log.e(tag, msg)
    fun e(tag: String, msg: String, t: Throwable?) = Log.e(tag, msg, t)
    fun v(tag: String, msg: String) = Log.v(tag, msg)
}

/**
 * UI log entry for SystemLogsManager (developer dashboard live log).
 * Separate from the DevLog object to avoid name clash.
 */
data class SystemLogEntry(
    val level: String,
    val message: String,
    val time: String,
    val color: Color
)

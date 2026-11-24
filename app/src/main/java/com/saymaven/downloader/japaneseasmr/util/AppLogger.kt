package com.saymaven.downloader.japaneseasmr.util

import android.util.Log
import com.saymaven.downloader.japaneseasmr.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized application logger that suppresses verbose/debug logs in release builds
 * while keeping a bounded in-memory ring buffer of warnings and errors for diagnostics.
 */
object AppLogger {
    private const val MAX_BUFFER_SIZE = 100
    private val buffer = ArrayDeque<String>(MAX_BUFFER_SIZE)
    private val lock = Any()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        }
        record("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        }
        record("E", tag, message, throwable)
    }

    fun exportLogs(): String {
        synchronized(lock) {
            if (buffer.isEmpty()) return "No diagnostic warnings or errors recorded."
            return buffer.joinToString("\n")
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
    }

    private fun record(level: String, tag: String, message: String, throwable: Throwable?) {
        val timestamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        val line = buildString {
            append(timestamp).append(" [").append(level).append("/").append(tag).append("] ").append(message)
            if (throwable != null) {
                append(" | ").append(throwable.javaClass.simpleName).append(": ").append(throwable.message)
            }
        }
        synchronized(lock) {
            if (buffer.size >= MAX_BUFFER_SIZE) {
                buffer.removeFirst()
            }
            buffer.addLast(line)
        }
    }
}

package org.vibevoyager.android

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VoyagrLogger {

    private const val FILE_NAME = "voyagr_debug.log"

    fun log(
        context: Context,
        tag: String,
        message: String
    ) {
        Log.d(tag, message)

        try {
            val timestamp =
                SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss.SSS",
                    Locale.UK
                ).format(Date())

            val line =
                "$timestamp [$tag] $message\n"

            File(
                context.filesDir,
                FILE_NAME
            ).appendText(line)

        } catch (e: Exception) {
            Log.e(
                "VOYAGR_LOG",
                "Failed to write log file",
                e
            )
        }
    }

    fun getLogFile(context: Context): File {
        return File(
            context.filesDir,
            FILE_NAME
        )
    }

    fun clear(context: Context) {
        try {
            getLogFile(context).writeText("")
        } catch (e: Exception) {
            Log.e(
                "VOYAGR_LOG",
                "Failed to clear log",
                e
            )
        }
    }
}
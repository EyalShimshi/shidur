package io.github.eyalshimshi.shidur

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Keeps the details of the last crash on the device, so the next launch can offer to copy them or
 * open a pre-filled GitHub issue. Nothing is sent anywhere automatically.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_REPORT_CHARS = 20_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                save(appContext, thread, throwable)
            } catch (ignored: Throwable) {
                // Reporting must never hide the original crash.
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    /** The report of the last crash, or null if the app hasn't crashed since it was dismissed. */
    fun lastReport(context: Context): String? {
        val file = reportFile(context)
        if (!file.exists()) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        reportFile(context).delete()
    }

    private fun save(context: Context, thread: Thread, throwable: Throwable) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        val report = buildString {
            appendLine("Shidur ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Time: $time, thread: ${thread.name}")
            appendLine()
            append(throwable.stackTraceToString())
        }
        reportFile(context).writeText(report.take(MAX_REPORT_CHARS))
    }

    private fun reportFile(context: Context) = File(context.filesDir, FILE_NAME)
}

package io.github.eyalshimshi.shidur.playback

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.eyalshimshi.shidur.MainActivity
import io.github.eyalshimshi.shidur.ShidurApplication
import io.github.eyalshimshi.shidur.data.AlarmConfig
import io.github.eyalshimshi.shidur.data.nextAlarmTime
import java.time.ZonedDateTime

/**
 * Schedules the wake-up alarm with AlarmManager.setAlarmClock: it rings on time even in battery
 * saving modes, and Android shows it as the next alarm in the status bar and on the lock screen.
 */
object AlarmScheduler {

    /** Fired by AlarmManager at the alarm time. */
    const val ACTION_FIRE = "io.github.eyalshimshi.shidur.action.ALARM_FIRE"

    /** Starts the playback service in alarm mode. */
    const val ACTION_START = "io.github.eyalshimshi.shidur.action.ALARM_START"
    const val EXTRA_STATION = "alarm_station"
    const val EXTRA_GENTLE = "alarm_gentle"

    private const val REQUEST_FIRE = 1
    private const val REQUEST_SHOW = 2

    /** Sets the alarm for [config], or cancels it; false when Android doesn't allow exact alarms. */
    fun apply(context: Context, config: AlarmConfig): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        val operation = firePendingIntent(context)
        alarmManager.cancel(operation)
        if (!config.enabled || config.stationId == null) return true
        if (!alarmManager.canScheduleExactAlarms()) return false
        val trigger = nextAlarmTime(ZonedDateTime.now(), config.hour, config.minute, config.days)
        val showIntent = PendingIntent.getActivity(
            context,
            REQUEST_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(trigger.toInstant().toEpochMilli(), showIntent), operation)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    private fun firePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_FIRE,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Starts the wake-up alarm when it fires, and sets it again after a restart, a change of time or
 * time zone, or an app update (Android forgets alarms in those cases).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store = (context.applicationContext as? ShidurApplication)?.container?.alarmStore ?: return
        if (intent.action == AlarmScheduler.ACTION_FIRE) {
            val config = store.current()
            val stationId = config.stationId
            if (config.enabled && stationId != null) {
                val start = Intent(context, PlaybackService::class.java)
                    .setAction(AlarmScheduler.ACTION_START)
                    .putExtra(AlarmScheduler.EXTRA_STATION, stationId)
                    .putExtra(AlarmScheduler.EXTRA_GENTLE, config.gentle)
                try {
                    // Exact alarms may start a foreground service even when the app is closed.
                    ContextCompat.startForegroundService(context, start)
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Couldn't start the wake-up alarm", e)
                }
                // A one-time alarm turns itself off; a repeating one moves on to its next day.
                if (config.days.isEmpty()) store.save(config.copy(enabled = false))
            }
        }
        AlarmScheduler.apply(context, store.current())
    }

    private companion object {
        const val TAG = "AlarmReceiver"
    }
}

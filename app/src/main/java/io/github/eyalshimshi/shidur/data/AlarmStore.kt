package io.github.eyalshimshi.shidur.data

import android.content.SharedPreferences
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The wake-up alarm: a time, the days it repeats on (none means once) and the station it plays. */
data class AlarmConfig(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val days: Set<DayOfWeek> = emptySet(),
    val stationId: String? = null,
    /** Starts quietly and gets louder over a minute. */
    val gentle: Boolean = true,
)

class AlarmStore(private val prefs: SharedPreferences) {

    private object Keys {
        const val ENABLED = "alarm_enabled"
        const val HOUR = "alarm_hour"
        const val MINUTE = "alarm_minute"
        const val DAYS = "alarm_days"
        const val STATION = "alarm_station"
        const val GENTLE = "alarm_gentle"
    }

    val config: Flow<AlarmConfig> = prefs.observe { read(it) }.distinctUntilChanged()

    fun current(): AlarmConfig = read(prefs)

    fun save(config: AlarmConfig) {
        prefs.edit()
            .putBoolean(Keys.ENABLED, config.enabled)
            .putInt(Keys.HOUR, config.hour)
            .putInt(Keys.MINUTE, config.minute)
            .putInt(Keys.DAYS, encodeDays(config.days))
            .putString(Keys.STATION, config.stationId)
            .putBoolean(Keys.GENTLE, config.gentle)
            .apply()
    }

    private fun read(p: SharedPreferences) = AlarmConfig(
        enabled = p.getBoolean(Keys.ENABLED, false),
        hour = p.getInt(Keys.HOUR, 7),
        minute = p.getInt(Keys.MINUTE, 0),
        days = decodeDays(p.getInt(Keys.DAYS, 0)),
        stationId = p.getString(Keys.STATION, null),
        gentle = p.getBoolean(Keys.GENTLE, true),
    )

    private fun encodeDays(days: Set<DayOfWeek>): Int = days.fold(0) { bits, day -> bits or (1 shl (day.value - 1)) }

    private fun decodeDays(bits: Int): Set<DayOfWeek> =
        DayOfWeek.values().filter { bits and (1 shl (it.value - 1)) != 0 }.toSet()
}

/** When an alarm set for [hour]:[minute] next rings after [now], on one of [days] (any day if empty). */
fun nextAlarmTime(now: ZonedDateTime, hour: Int, minute: Int, days: Set<DayOfWeek>): ZonedDateTime {
    var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!next.isAfter(now)) next = next.plusDays(1)
    if (days.isNotEmpty()) {
        repeat(DAYS_IN_WEEK) { if (next.dayOfWeek !in days) next = next.plusDays(1) }
    }
    return next
}

private const val DAYS_IN_WEEK = 7

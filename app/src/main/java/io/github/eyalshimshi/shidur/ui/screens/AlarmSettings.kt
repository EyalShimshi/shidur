package io.github.eyalshimshi.shidur.ui.screens

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.data.nextAlarmTime
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.ui.ShidurViewModel
import io.github.eyalshimshi.shidur.ui.components.AppIcons
import io.github.eyalshimshi.shidur.ui.components.StationArtwork
import io.github.eyalshimshi.shidur.ui.openExactAlarmSettings
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Calendar

/** Settings for the wake-up alarm: on/off, time, repeat days, station and gentle start. */
@Composable
internal fun AlarmGroup(viewModel: ShidurViewModel, hebrew: Boolean) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val alarm by viewModel.alarm.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    var exactAlarmsAllowed by remember { mutableStateOf(true) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showStationPicker by remember { mutableStateOf(false) }

    // Coming back from the system permission screen: check again and set the alarm.
    LifecycleResumeEffect(Unit) {
        exactAlarmsAllowed = viewModel.canScheduleExactAlarms()
        viewModel.refreshAlarm()
        onPauseOrDispose { }
    }

    val stationName = alarm.stationId?.let { library.stationsById[it]?.name(hebrew) }
        ?: stringResource(R.string.alarm_no_station)
    val summary = if (alarm.enabled) {
        val now = ZonedDateTime.now()
        val next = nextAlarmTime(now, alarm.hour, alarm.minute, alarm.days)
        val minutes = (Duration.between(now, next).seconds + 59) / 60
        val until = if (minutes >= 60) {
            stringResource(R.string.alarm_duration_hours, minutes / 60, minutes % 60)
        } else {
            stringResource(R.string.alarm_duration_minutes, minutes)
        }
        stringResource(R.string.alarm_rings_in, until, stationName)
    } else {
        stringResource(R.string.alarm_off)
    }
    val daysText = when (alarm.days.size) {
        0 -> stringResource(R.string.alarm_once)
        WEEK.size -> stringResource(R.string.alarm_every_day)
        else -> WEEK.filter { it in alarm.days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    SettingsGroup(stringResource(R.string.alarm_title)) {
        SwitchRow(
            title = stringResource(R.string.alarm_enable),
            summary = summary,
            checked = alarm.enabled,
            onChange = { viewModel.updateAlarm(alarm.copy(enabled = it)) },
        )
        if (alarm.enabled && !exactAlarmsAllowed) {
            ClickRow(
                title = stringResource(R.string.alarm_permission),
                summary = stringResource(R.string.alarm_permission_summary),
                onClick = { openExactAlarmSettings(context) },
            )
        }
        ClickRow(
            title = stringResource(R.string.alarm_time),
            summary = formatTime(context, alarm.hour, alarm.minute),
            onClick = { showTimePicker = true },
        )
        GroupCard {
            AlarmDays(
                days = alarm.days,
                summary = daysText,
                locale = locale,
                onChange = { viewModel.updateAlarm(alarm.copy(days = it)) },
            )
        }
        ClickRow(
            title = stringResource(R.string.alarm_station),
            summary = stationName,
            onClick = { showStationPicker = true },
        )
        SwitchRow(
            title = stringResource(R.string.alarm_gentle),
            summary = stringResource(R.string.alarm_gentle_summary),
            checked = alarm.gentle,
            onChange = { viewModel.updateAlarm(alarm.copy(gentle = it)) },
        )
        GroupCard {
            Text(
                text = stringResource(R.string.alarm_backup_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (showTimePicker) {
        AlarmTimeDialog(
            hour = alarm.hour,
            minute = alarm.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                showTimePicker = false
                // Choosing a time means the user wants the alarm.
                viewModel.updateAlarm(alarm.copy(hour = hour, minute = minute, enabled = true))
            },
        )
    }
    if (showStationPicker) {
        val stations = remember(library) {
            library.favorites + library.stations.filter { it.id !in library.favoriteIds }
        }
        AlarmStationDialog(
            stations = stations,
            selectedId = alarm.stationId,
            hebrew = hebrew,
            onDismiss = { showStationPicker = false },
            onSelect = { stationId ->
                showStationPicker = false
                viewModel.updateAlarm(alarm.copy(stationId = stationId))
            },
        )
    }
}

/** One round toggle per weekday, Sunday first as in Israel; none selected means "once". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmDays(
    days: Set<DayOfWeek>,
    summary: String,
    locale: java.util.Locale,
    onChange: (Set<DayOfWeek>) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.alarm_repeat), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WEEK.forEach { day ->
                val selected = day in days
                val fullName = day.getDisplayName(TextStyle.FULL, locale)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { checked -> onChange(if (checked) days + day else days - day) },
                        )
                        .semantics { contentDescription = fullName },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = hour,
        initialMinute = minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun AlarmStationDialog(
    stations: List<Station>,
    selectedId: String?,
    hebrew: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        title = { Text(stringResource(R.string.alarm_station)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(stations, key = { it.id }) { station ->
                    ListItem(
                        headlineContent = { Text(station.name(hebrew)) },
                        leadingContent = { StationArtwork(station, 40.dp) },
                        trailingContent = if (station.id == selectedId) {
                            { Icon(AppIcons.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(station.id) },
                    )
                }
            }
        },
    )
}

/** The time in the phone's format (24-hour or AM/PM). */
private fun formatTime(context: Context, hour: Int, minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
    }
    return DateFormat.getTimeFormat(context).format(calendar.time)
}

private val WEEK = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)

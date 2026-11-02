package io.github.eyalshimshi.shidur.ui.screens

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.eyalshimshi.shidur.BuildConfig
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.data.CatalogRepository
import io.github.eyalshimshi.shidur.data.DataSaverMode
import io.github.eyalshimshi.shidur.data.REPOSITORY_URL
import io.github.eyalshimshi.shidur.data.ThemeMode
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.StartRecord
import io.github.eyalshimshi.shidur.ui.ShidurViewModel
import io.github.eyalshimshi.shidur.ui.components.AppIcons
import io.github.eyalshimshi.shidur.ui.components.GroupShapes
import io.github.eyalshimshi.shidur.ui.components.LocalBottomContentPadding
import io.github.eyalshimshi.shidur.ui.components.StationArtwork
import io.github.eyalshimshi.shidur.ui.components.secondsText
import io.github.eyalshimshi.shidur.ui.reportProblemUrl
import io.github.eyalshimshi.shidur.ui.suggestStationUrl
import io.github.eyalshimshi.shidur.ui.theme.ACCENT_PRESETS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    catalogVersion: Long,
    stationsById: Map<String, Station>,
    hebrew: Boolean,
    viewModel: ShidurViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val starts by viewModel.starts.collectAsStateWithLifecycle()
    val bottomPadding = LocalBottomContentPadding.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_colors)) {
                ThemeModeCard(selected = settings.themeMode, onSelect = viewModel::setThemeMode)
                SwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    checked = settings.dynamicColor,
                    onChange = viewModel::setDynamicColor,
                )
                AccentCard(
                    selected = if (settings.dynamicColor) null else settings.accentColor ?: ACCENT_PRESETS.first(),
                    onSelect = viewModel::setAccentColor,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_pure_black),
                    summary = stringResource(R.string.settings_pure_black_summary),
                    checked = settings.pureBlack,
                    onChange = viewModel::setPureBlack,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_station_tint),
                    summary = null,
                    checked = settings.stationTint,
                    onChange = viewModel::setStationTint,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_station_logos),
                    summary = stringResource(R.string.settings_station_logos_summary),
                    checked = settings.stationLogos,
                    onChange = viewModel::setStationLogos,
                )
            }

            SettingsGroup(stringResource(R.string.settings_interface)) {
                SwitchRow(
                    title = stringResource(R.string.settings_background_gradient),
                    summary = stringResource(R.string.settings_background_gradient_summary),
                    checked = settings.backgroundGradient,
                    onChange = viewModel::setBackgroundGradient,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_animations),
                    summary = stringResource(R.string.settings_animations_summary),
                    checked = settings.animations,
                    onChange = viewModel::setAnimations,
                )
            }

            SettingsGroup(stringResource(R.string.settings_language)) {
                val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                val currentLanguage = when {
                    currentTags.startsWith("he") || currentTags.startsWith("iw") -> "he"
                    currentTags.startsWith("en") -> "en"
                    else -> ""
                }
                GroupCard {
                    Column {
                        LANGUAGES.forEach { (tag, label) ->
                            RadioRow(
                                title = stringResource(label),
                                selected = currentLanguage == tag,
                                onSelect = {
                                    AppCompatDelegate.setApplicationLocales(
                                        if (tag.isEmpty()) {
                                            LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            LocaleListCompat.forLanguageTags(tag)
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            SettingsGroup(stringResource(R.string.settings_playback)) {
                GroupCard {
                    Column {
                        Text(
                            text = stringResource(R.string.settings_data_saver),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                        DataSaverMode.entries.forEach { mode ->
                            RadioRow(
                                title = stringResource(
                                    when (mode) {
                                        DataSaverMode.OFF -> R.string.data_saver_off
                                        DataSaverMode.MOBILE -> R.string.data_saver_mobile
                                        DataSaverMode.ALWAYS -> R.string.data_saver_always
                                    },
                                ),
                                selected = settings.dataSaver == mode,
                                onSelect = { viewModel.setDataSaver(mode) },
                            )
                        }
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.settings_instant_start),
                    summary = stringResource(R.string.settings_instant_start_summary),
                    checked = settings.instantStart,
                    onChange = viewModel::setInstantStart,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_skip_intros),
                    summary = stringResource(R.string.settings_skip_intros_summary),
                    checked = settings.skipIntros,
                    onChange = viewModel::setSkipIntros,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_sleep_fade),
                    summary = null,
                    checked = settings.sleepFadeOut,
                    onChange = viewModel::setSleepFadeOut,
                )
                SwitchRow(
                    title = stringResource(R.string.settings_resume_headphones),
                    summary = stringResource(R.string.settings_resume_headphones_summary),
                    checked = settings.resumeOnHeadphones,
                    onChange = viewModel::setResumeOnHeadphones,
                )
            }

            AlarmGroup(viewModel = viewModel, hebrew = hebrew)

            SettingsGroup(stringResource(R.string.settings_stations)) {
                ClickRow(
                    title = stringResource(R.string.settings_check_updates),
                    summary = stringResource(R.string.settings_catalog_version, catalogVersion.toString()),
                    onClick = {
                        viewModel.refreshCatalog { result ->
                            val message = when (result) {
                                CatalogRepository.RefreshResult.UPDATED -> R.string.catalog_updated
                                CatalogRepository.RefreshResult.UP_TO_DATE -> R.string.catalog_up_to_date
                                CatalogRepository.RefreshResult.FAILED -> R.string.catalog_update_failed
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                ClickRow(
                    title = stringResource(R.string.settings_suggest_station),
                    summary = null,
                    onClick = { uriHandler.openUri(suggestStationUrl()) },
                )
                ClickRow(
                    title = stringResource(R.string.action_report),
                    summary = null,
                    onClick = { uriHandler.openUri(reportProblemUrl(null)) },
                )
                ClickRow(
                    title = stringResource(R.string.settings_clear_recents),
                    summary = null,
                    onClick = {
                        viewModel.clearRecents()
                        Toast.makeText(context, R.string.recents_cleared, Toast.LENGTH_SHORT).show()
                    },
                )
            }

            SettingsGroup(stringResource(R.string.settings_performance)) {
                PerformanceItems(
                    starts = starts,
                    stationsById = stationsById,
                    hebrew = hebrew,
                    onClear = viewModel::clearDiagnostics,
                )
            }

            SettingsGroup(stringResource(R.string.settings_about)) {
                ClickRow(
                    title = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    summary = stringResource(R.string.about_license),
                    onClick = { uriHandler.openUri(REPOSITORY_URL) },
                )
                GroupCard {
                    Text(
                        text = stringResource(R.string.about_disclaimer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp + bottomPadding))
        }
    }
}

/** How long recent stations took from the tap until sound, with what helped. */
@Composable
private fun PerformanceItems(
    starts: List<StartRecord>,
    stationsById: Map<String, Station>,
    hebrew: Boolean,
    onClear: () -> Unit,
) {
    if (starts.isEmpty()) {
        GroupCard {
            Text(
                text = stringResource(R.string.perf_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    val average = starts.map { it.millis }.average().toLong()
    GroupCard {
        ListItem(
            headlineContent = { Text(stringResource(R.string.perf_average, secondsText(average))) },
            supportingContent = { Text(stringResource(R.string.perf_average_summary, starts.size)) },
            trailingContent = { TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) } },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
    val instantLabel = stringResource(R.string.perf_instant)
    val warmedLabel = stringResource(R.string.perf_warmed)
    val skippedLabel = stringResource(R.string.perf_skipped_redirect)
    starts.take(MAX_SHOWN_STARTS).forEach { record ->
        val station = stationsById[record.stationId]
        val attemptsLabel = if (record.attempts > 1) stringResource(R.string.perf_attempts, record.attempts) else null
        val details = listOfNotNull(
            record.host.takeIf { it.isNotEmpty() },
            when {
                record.instant -> instantLabel
                record.skippedRedirect -> skippedLabel
                record.warmed -> warmedLabel
                else -> null
            },
            attemptsLabel,
        ).joinToString(" · ")
        GroupCard {
            ListItem(
                headlineContent = { Text(station?.name(hebrew) ?: record.stationId) },
                supportingContent = { Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                leadingContent = if (station != null) {
                    { StationArtwork(station, 36.dp) }
                } else {
                    null
                },
                trailingContent = { Text(secondsText(record.millis), style = MaterialTheme.typography.titleSmall) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

private const val MAX_SHOWN_STARTS = 10

/** A titled group of cards: large outer corners, small corners between its items. */
@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupShapes.Outer)),
        verticalArrangement = Arrangement.spacedBy(GroupShapes.Gap),
        content = content,
    )
}

@Composable
internal fun GroupCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(GroupShapes.Inner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
internal fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    GroupCard {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (summary != null) {
                { Text(summary) }
            } else {
                null
            },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        )
    }
}

/** A radio option; place inside a [GroupCard] together with the other options. */
@Composable
private fun RadioRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    )
}

@Composable
internal fun ClickRow(title: String, summary: String?, onClick: () -> Unit) {
    GroupCard {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = if (summary != null) {
                { Text(summary) }
            } else {
                null
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.selectable(selected = false, onClick = onClick, role = Role.Button),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeCard(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    GroupCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selected == mode,
                        onClick = { onSelect(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Accent color swatches; [selected] is null while wallpaper colors are in use. */
@Composable
private fun AccentCard(selected: Int?, onSelect: (Int) -> Unit) {
    GroupCard {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.settings_accent),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_accent_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ACCENT_PRESETS.forEachIndexed { index, color ->
                    AccentSwatch(
                        color = Color(color),
                        selected = selected == color,
                        label = stringResource(R.string.accent_option, index + 1),
                        onClick = { onSelect(color) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    val ring = if (selected) {
        Modifier
            .border(width = 3.dp, color = MaterialTheme.colorScheme.onSurface, shape = CircleShape)
            .padding(6.dp)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
            .then(ring)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
    }
}

private val LANGUAGES = listOf(
    "" to R.string.language_system,
    "he" to R.string.language_hebrew,
    "en" to R.string.language_english,
)

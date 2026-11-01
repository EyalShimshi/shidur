package io.github.eyalshimshi.shidur.ui.screens

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import io.github.eyalshimshi.shidur.BuildConfig
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.data.CatalogRepository
import io.github.eyalshimshi.shidur.data.DataSaverMode
import io.github.eyalshimshi.shidur.data.REPOSITORY_URL
import io.github.eyalshimshi.shidur.data.ThemeMode
import io.github.eyalshimshi.shidur.ui.ShidurViewModel
import io.github.eyalshimshi.shidur.ui.reportProblemUrl
import io.github.eyalshimshi.shidur.ui.suggestStationUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    catalogVersion: Long,
    viewModel: ShidurViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsSection(stringResource(R.string.settings_appearance))
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
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
            SwitchRow(
                title = stringResource(R.string.settings_pure_black),
                summary = stringResource(R.string.settings_pure_black_summary),
                checked = settings.pureBlack,
                onChange = viewModel::setPureBlack,
            )
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = settings.dynamicColor,
                onChange = viewModel::setDynamicColor,
            )
            SwitchRow(
                title = stringResource(R.string.settings_station_tint),
                summary = null,
                checked = settings.stationTint,
                onChange = viewModel::setStationTint,
            )

            SettingsSection(stringResource(R.string.settings_language))
            val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            val currentLanguage = when {
                currentTags.startsWith("he") || currentTags.startsWith("iw") -> "he"
                currentTags.startsWith("en") -> "en"
                else -> ""
            }
            LANGUAGES.forEach { (tag, label) ->
                RadioRow(
                    title = stringResource(label),
                    selected = currentLanguage == tag,
                    onSelect = {
                        AppCompatDelegate.setApplicationLocales(
                            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
                        )
                    },
                )
            }

            SettingsSection(stringResource(R.string.settings_playback))
            Text(
                text = stringResource(R.string.settings_data_saver),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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

            SettingsSection(stringResource(R.string.settings_stations))
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

            SettingsSection(stringResource(R.string.settings_about))
            ClickRow(
                title = stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                summary = stringResource(R.string.about_license),
                onClick = { uriHandler.openUri(REPOSITORY_URL) },
            )
            Text(
                text = stringResource(R.string.about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary != null) {
            { Text(summary) }
        } else {
            null
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
    )
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    )
}

@Composable
private fun ClickRow(title: String, summary: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary != null) {
            { Text(summary) }
        } else {
            null
        },
        modifier = Modifier.selectable(selected = false, onClick = onClick, role = Role.Button),
    )
}

private val LANGUAGES = listOf(
    "" to R.string.language_system,
    "he" to R.string.language_hebrew,
    "en" to R.string.language_english,
)

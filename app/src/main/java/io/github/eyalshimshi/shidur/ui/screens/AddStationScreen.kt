package io.github.eyalshimshi.shidur.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.playback.StreamProbe
import io.github.eyalshimshi.shidur.ui.components.AppIcons
import io.github.eyalshimshi.shidur.ui.components.LocalBottomContentPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStationScreen(
    initialUrl: String?,
    onBack: () -> Unit,
    onProbe: suspend (String) -> StreamProbe.Result,
    onSave: (name: String, url: String) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<StreamProbe.Result?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.add_station_title)) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    result = null
                    failed = false
                },
                label = { Text(stringResource(R.string.add_station_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.add_station_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        failed = false
                        result = null
                        scope.launch {
                            val probed = try {
                                onProbe(normalizeStreamUrl(url))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                null
                            }
                            testing = false
                            result = probed
                            failed = probed == null
                            if (probed != null && name.isBlank()) name = probed.stationName.orEmpty()
                        }
                    },
                    enabled = url.isNotBlank() && !testing,
                ) {
                    Text(stringResource(if (testing) R.string.add_station_testing else R.string.add_station_test))
                }
                if (testing) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            }
            result?.let { probed ->
                Spacer(Modifier.height(12.dp))
                val details = listOfNotNull(
                    probed.stationName,
                    probed.contentType,
                    probed.bitrate?.let { "$it kbps" },
                ).joinToString(" · ")
                Text(
                    text = stringResource(R.string.add_station_ok, details),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (failed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.add_station_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onSave(name.trim(), result?.streamUrl ?: normalizeStreamUrl(url)) },
                enabled = url.isNotBlank() && name.isNotBlank() && !testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (failed) R.string.add_station_save_anyway else R.string.add_station_save))
            }
            Spacer(Modifier.height(LocalBottomContentPadding.current))
        }
    }
}

/** Adds "http://" when the user typed an address without a scheme. */
private fun normalizeStreamUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

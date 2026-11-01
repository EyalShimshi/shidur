package io.github.eyalshimshi.shidur.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.ui.copyToClipboard
import io.github.eyalshimshi.shidur.ui.crashReportUrl

/** Offers the details of the last crash, so they can be sent to the developer. */
@Composable
fun CrashDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val error = report.substringAfter("\n\n").lineSequence().take(PREVIEW_LINES).joinToString("\n")
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.BugReport, contentDescription = null) },
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column {
                Text(stringResource(R.string.crash_body))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    copyToClipboard(context, report)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.crash_copy))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) }
                TextButton(
                    onClick = {
                        uriHandler.openUri(crashReportUrl(report))
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.crash_report))
                }
            }
        },
    )
}

private const val PREVIEW_LINES = 4

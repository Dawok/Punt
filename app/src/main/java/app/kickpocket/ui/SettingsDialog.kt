package app.kickpocket.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.data.AppPreferences
import app.kickpocket.player.QualityPreferences
import app.kickpocket.update.UpdateState
import kotlin.math.roundToInt
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext

@Composable
fun SettingsDialog(
    preferences: AppPreferences,
    onAnimatedEmotes: (Boolean) -> Unit,
    onFontSize: (Int) -> Unit,
    onClearRecent: () -> Unit,
    onDismiss: () -> Unit,
    onWifiQuality: (Int) -> Unit,
    onMobileQuality: (Int) -> Unit,
    updates: UpdateState,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make yourself comfortable", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Animated emotes", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Text("Switch off to reduce motion and battery use.", fontSize = 12.sp, lineHeight = 18.sp)
                    }
                    Switch(checked = preferences.animatedEmotes, onCheckedChange = onAnimatedEmotes,
                        modifier = Modifier.semantics { contentDescription = "Animated emotes" })
                }
                HorizontalDivider()
                Text("Video quality", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                QualitySetting("Wi-Fi", preferences.wifiQuality, onWifiQuality)
                QualitySetting("Mobile data", preferences.mobileQuality, onMobileQuality)
                Text("Your preference applies across channels. Lower resolutions stay adaptive up to the chosen quality.", fontSize = 12.sp, lineHeight = 18.sp)
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Chat text size", color = MaterialTheme.colorScheme.onSurface)
                    Text("${preferences.chatFontSize}", color = KickGreen)
                }
                Slider(
                    value = preferences.chatFontSize.toFloat(), onValueChange = { onFontSize(it.roundToInt()) },
                    valueRange = 12f..20f, steps = 7,
                    modifier = Modifier.semantics { contentDescription = "Chat text size" },
                )
                Text("viewer  Looks good from here.", fontSize = preferences.chatFontSize.sp, color = MaterialTheme.colorScheme.onSurface)
                HorizontalDivider()
                TextButton(onClick = onClearRecent, enabled = preferences.recentChannels.isNotEmpty()) { Text("Clear recent channels") }
                TextButton(onClick = {
                    val action = if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
                        else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    context.startActivity(Intent(action, Uri.parse("package:${context.packageName}")))
                }) { Text("Open Kick links in Punt") }
                HorizontalDivider()
                Text("App updates", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                updates.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                when {
                    updates.downloading -> {
                        Text(if (updates.progress != null) "Downloading · ${updates.progress}%" else "Downloading update…", fontSize = 12.sp)
                        if (updates.progress != null) LinearProgressIndicator(progress = { updates.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = onCancelDownload) { Text("Cancel download") }
                    }
                    updates.ready -> {
                        Text("${updates.available?.versionName} is ready to install.", fontSize = 12.sp)
                        Button(onClick = onInstallUpdate) { Text("Install update") }
                        Text("Android will ask you to confirm installation.", fontSize = 11.sp)
                    }
                    updates.available != null -> {
                        Text("Version ${updates.available.versionName} is available.", fontSize = 12.sp)
                        Button(onClick = onDownloadUpdate, enabled = !updates.checking) { Text("Download update") }
                    }
                    updates.checked && updates.error == null -> Text("You're up to date.", fontSize = 12.sp)
                }
                TextButton(onClick = onCheckUpdates, enabled = !updates.checking && !updates.downloading) {
                    Text(if (updates.checking) "Checking…" else "Check for updates")
                }
                Text("Punt ${app.kickpocket.BuildConfig.VERSION_NAME} · Unofficial client\nLog in to join chat and follow channels.", fontSize = 11.sp, lineHeight = 17.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun QualitySetting(label: String, value: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = "$label video quality" }) {
                Text(QualityPreferences.label(value))
            }
            DropdownMenu(expanded, { expanded = false }) {
                QualityPreferences.choices.forEach { height ->
                    DropdownMenuItem(text = { Text(QualityPreferences.label(height)) }, onClick = { onChange(height); expanded = false })
                }
            }
        }
    }
}


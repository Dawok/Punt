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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.data.AppPreferences
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    preferences: AppPreferences,
    onAnimatedEmotes: (Boolean) -> Unit,
    onFontSize: (Int) -> Unit,
    onClearRecent: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                Text("Punt ${app.kickpocket.BuildConfig.VERSION_NAME} · Unofficial client\nLog in to join chat and follow channels.", fontSize = 11.sp, lineHeight = 17.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}


package app.kickpocket.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.kickpocket.alerts.LiveAlerts
import app.kickpocket.data.AppPreferences
import app.kickpocket.data.RecentChannel

@Composable
internal fun LiveAlertsDialog(preferences: AppPreferences, current: RecentChannel?, onToggle: (RecentChannel) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val channels = (listOfNotNull(current) + preferences.liveAlertChannels + preferences.favoriteChannels + preferences.recentChannels)
        .distinctBy { it.slug }.take(50)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Live alerts") }, text = {
        Column(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Check selected channels about every 15 minutes. Android may delay alerts to save battery.", fontSize = 12.sp)
            if (!LiveAlerts.permitted(context)) TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text("Allow notifications in Android settings") }
            if (channels.isEmpty()) Text("Open or pin a channel to add live alerts.")
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(channels, key = { it.slug }) { channel ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(channel.displayName, Modifier.weight(1f))
                        Switch(preferences.liveAlertChannels.any { it.slug == channel.slug }, onCheckedChange = { onToggle(channel) },
                            modifier = Modifier.semantics { contentDescription = "Live alerts for ${channel.displayName}" })
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

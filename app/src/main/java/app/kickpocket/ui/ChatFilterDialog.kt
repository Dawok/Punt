package app.kickpocket.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.kickpocket.chat.ChatFilters
import app.kickpocket.data.AppPreferences

@Composable
internal fun ChatFilterDialog(preferences: AppPreferences, onSave: (List<String>, List<String>, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit) {
    var users by remember { mutableStateOf(preferences.hiddenChatUsers.joinToString("\n")) }
    var keywords by remember { mutableStateOf(preferences.hiddenChatKeywords.joinToString("\n")) }
    var repeats by remember { mutableStateOf(preferences.reduceRepeatedMessages) }
    var mentions by remember { mutableStateOf(preferences.highlightMentions) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Chat filters") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(users, { users = it.take(10_000) }, label = { Text("Hidden users") },
                supportingText = { Text("One username per line. You can include bot usernames.") }, minLines = 2, maxLines = 4)
            OutlinedTextField(keywords, { keywords = it.take(10_000) }, label = { Text("Hidden words or phrases") },
                supportingText = { Text("One per line; matching ignores capitalization.") }, minLines = 2, maxLines = 4)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Reduce repeated messages", Modifier.weight(1f)); Switch(repeats, { repeats = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Highlight mentions of me", Modifier.weight(1f)); Switch(mentions, { mentions = it })
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(ChatFilters.terms(users), ChatFilters.terms(keywords), repeats, mentions); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

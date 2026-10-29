package app.kickpocket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.data.*

@Composable
internal fun SearchResults(state: KickSearchState, onOpen: (String) -> Unit, onCategory: (CategoryCard) -> Unit, onRetry: () -> Unit) {
    if (state.query.isBlank()) return
    val scroll = rememberScrollState()
    LaunchedEffect(state.query) { scroll.scrollTo(0) }
    Surface(color = PanelColor, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(scroll).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = onRetry) { Text("Retry search") } }
            if (state.result.channels.isNotEmpty()) Text("Channels", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            state.result.channels.forEach { channel ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpen(channel.slug) }, verticalAlignment = Alignment.CenterVertically) {
                    Avatar(channel.avatar, channel.name, 30)
                    Text(channel.name, Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 13.sp)
                    Text(if (channel.live) "LIVE" else "Offline", color = if (channel.live) KickGreen else SubtleColor, fontSize = 10.sp)
                }
            }
            if (state.result.categories.isNotEmpty()) Text("Categories", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            state.result.categories.forEach { category ->
                TextButton(onClick = { onCategory(category) }, modifier = Modifier.fillMaxWidth()) { Text(category.name, Modifier.fillMaxWidth()) }
            }
            if (!state.loading && state.error == null && state.result.channels.isEmpty() && state.result.categories.isEmpty())
                Text("No channels or categories found.", color = SubtleColor)
        }
    }
}

package app.kickpocket.ui

import app.kickpocket.BrowseState
import app.kickpocket.BrowseTab
import app.kickpocket.data.BrowseSort
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.ui.platform.testTag
import app.kickpocket.data.AccountState
import app.kickpocket.data.CategoryCard
import app.kickpocket.data.ChannelCard
import app.kickpocket.data.RecentChannel
import app.kickpocket.data.ChannelSuggestions
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kickpocket.StreamState
import app.kickpocket.data.AppPreferences
import coil3.compose.AsyncImage

@Composable
fun HomeScreen(
    state: StreamState,
    preferences: AppPreferences,
    browse: BrowseState,
    account: AccountState,
    onOpen: (String) -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    onSignOut: () -> Unit,
    onTab: (BrowseTab) -> Unit,
    onSort: (BrowseSort) -> Unit,
    onCategory: (CategoryCard) -> Unit,
    onCategoryBack: () -> Unit,
    onRefresh: () -> Unit,
    onMore: () -> Unit,
    followedChannels: List<ChannelCard>,
    onPin: (RecentChannel) -> Unit,
    updateVersion: String?,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var sortMenu by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(browse.tab, browse.category?.id, browse.sort) { gridState.scrollToItem(0) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark()
                Text("PUNT", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 10.dp).weight(1f))
                Box {
                    TextButton(onClick = { if (account.account == null) onLogin() else accountMenu = true }) {
                        Text(account.account?.username ?: "Log in", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 110.dp))
                    }
                    DropdownMenu(accountMenu, { accountMenu = false }) {
                        DropdownMenuItem(text = { Text("Sign out") }, onClick = { accountMenu = false; onSignOut() })
                    }
                }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = SubtleColor) }
            }
        }
        if (updateVersion != null) item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(color = PanelColor, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Update available · $updateVersion", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSettings) { Text("Update") }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Channel name or Kick link") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, enabled = !state.loading,
                    trailingIcon = {
                        IconButton(
                            onClick = { onOpen(input) },
                            enabled = input.isNotBlank() && !state.loading,
                            modifier = Modifier.testTag("watch-channel").semantics {
                                contentDescription = if (state.loading) "Opening channel" else "Watch channel"
                            },
                        ) {
                            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Icon(Icons.AutoMirrored.Filled.ArrowForward, null,
                                tint = if (input.isNotBlank()) KickGreen else SubtleColor)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (input.isNotBlank() && !state.loading) onOpen(input) }),
                )
                state.error?.let { ErrorCard(it) }
                val suggestions = ChannelSuggestions.find(input, preferences, followedChannels, browse.channels)
                if (suggestions.isNotEmpty()) Surface(color = PanelColor, shape = RoundedCornerShape(12.dp)) {
                    Column {
                        suggestions.forEach { suggestion ->
                            val channel = suggestion.channel
                            Row(Modifier.fillMaxWidth().clickable(enabled = !state.loading) { onOpen(channel.slug) }
                                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(channel.avatarUrl, channel.displayName, 30)
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(channel.displayName, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${channel.slug} · ${suggestion.source}", fontSize = 11.sp, color = SubtleColor)
                                }
                                PinButton(preferences.favoriteChannels.any { it.slug == channel.slug }, channel.displayName) { onPin(channel) }
                            }
                        }
                    }
                }
            }
        }
        if (preferences.favoriteChannels.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pinned channels", fontSize = 12.sp, color = SubtleColor)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(preferences.favoriteChannels, key = { it.slug }) { channel ->
                            Surface(shape = RoundedCornerShape(12.dp), color = PanelColor,
                                modifier = Modifier.clickable(enabled = !state.loading) { onOpen(channel.slug) }) {
                                Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(channel.avatarUrl, channel.displayName, 28)
                                    Text(channel.displayName, fontSize = 12.sp, maxLines = 1,
                                        modifier = Modifier.padding(start = 8.dp).widthIn(max = 110.dp), overflow = TextOverflow.Ellipsis)
                                    PinButton(true, channel.displayName) { onPin(channel) }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (preferences.recentChannels.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(preferences.recentChannels, key = { it.slug }) { channel ->
                        Surface(shape = RoundedCornerShape(12.dp), color = PanelColor,
                            modifier = Modifier.clickable(enabled = !state.loading) { onOpen(channel.slug) }) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(channel.avatarUrl, channel.displayName, 28)
                                Text(channel.displayName, fontSize = 12.sp, maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 110.dp), overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrowseTab.entries.forEach { tab ->
                    FilterChip(selected = browse.tab == tab, onClick = { onTab(tab) },
                        label = { Text(tab.label, fontSize = 13.sp) }, modifier = Modifier.weight(1f))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (browse.category != null) IconButton(onClick = onCategoryBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to categories") }
                Text(browse.category?.name ?: when (browse.tab) {
                    BrowseTab.Live -> "Live channels"
                    BrowseTab.Categories -> "Browse categories"
                    BrowseTab.Following -> "Following · live now"
                }, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onRefresh, enabled = !browse.loading) { Icon(Icons.Default.Refresh, "Refresh channels", tint = SubtleColor) }
            }
        }
        if (browse.tab != BrowseTab.Following) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box {
                    OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("browse-sort")) {
                        Text(browse.sort.label, fontSize = 13.sp)
                        Icon(Icons.Default.ExpandMore, null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                    }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        BrowseSort.entries.forEach { sort ->
                            DropdownMenuItem(text = { Text(sort.label) }, onClick = { sortMenu = false; onSort(sort) })
                        }
                    }
                }
            }
        }
        if ((browse.tab == BrowseTab.Following || browse.sort == BrowseSort.Recommended) && account.account == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(color = PanelColor, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (browse.tab == BrowseTab.Following) "Your channels, live now" else "Recommended for you", fontWeight = FontWeight.SemiBold)
                        Text(if (browse.tab == BrowseTab.Following) "Log in to see your live followed channels and join chat." else "Log in to see Kick’s personalized recommendations.", color = SubtleColor, fontSize = 13.sp)
                        Button(onClick = onLogin) { Text("Log in to Kick") }
                    }
                }
            }
        } else {
            browse.error?.let { error ->
                item(span = { GridItemSpan(maxLineSpan) }) { ErrorCard(error) }
            }
            if (browse.tab == BrowseTab.Categories && browse.category == null) {
                items(browse.categories, key = { "category:${it.id}" }) { category ->
                    Surface(shape = RoundedCornerShape(12.dp), color = PanelColor, modifier = Modifier.clickable { onCategory(category) }) {
                        Column {
                            AsyncImage(model = category.image, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                            Column(Modifier.padding(12.dp)) {
                                Text(category.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${compactCount(category.viewers)} watching", color = SubtleColor, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
            } else {
                items(browse.channels, key = { "channel:${it.slug}" }) { channel ->
                    ChannelTile(channel, enabled = !state.loading, onClick = { onOpen(channel.slug) },
                        pinned = preferences.favoriteChannels.any { it.slug == channel.slug },
                        onPin = { onPin(RecentChannel(channel.slug, channel.username, channel.avatar)) })
                }
            }
            if (browse.loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = KickGreen, strokeWidth = 2.dp) }
                }
            } else if (browse.cursor != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
                }
            } else if (browse.tab == BrowseTab.Categories && browse.category == null && browse.categories.isEmpty() && browse.error == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("No categories are available right now. Try refreshing.", color = SubtleColor, fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp))
                }
            } else if (browse.channels.isEmpty() && (browse.tab != BrowseTab.Categories || browse.category != null) && browse.error == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (browse.tab == BrowseTab.Following) "None of your followed channels are live right now." else "No live streams in this category right now.",
                        color = SubtleColor, fontSize = 13.sp, modifier = Modifier.padding(vertical = 24.dp))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Unofficial Kick client · Kick + 7TV emotes", color = SubtleColor, fontSize = 11.sp) }
    }
}

@Composable
private fun ChannelTile(channel: ChannelCard, enabled: Boolean, onClick: () -> Unit, pinned: Boolean, onPin: () -> Unit) {
    Surface(color = PanelColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.clickable(enabled = enabled, onClick = onClick)) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                if (channel.thumbnail != null) AsyncImage(model = channel.thumbnail, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Box(Modifier.align(Alignment.Center)) { Avatar(channel.avatar, channel.username, 48) }
                Box(Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 12.dp))) {
                    PinButton(pinned, channel.username, onPin)
                }
                Text("LIVE · ${compactCount(channel.viewers)}", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(7.dp).clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 6.dp, vertical = 3.dp))
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(channel.username, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(channel.title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (channel.category.isNotBlank()) Text(channel.category, color = SubtleColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun PinButton(pinned: Boolean, name: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(if (pinned) Icons.Default.Star else Icons.Outlined.StarBorder,
            if (pinned) "Unpin $name" else "Pin $name", tint = if (pinned) KickGreen else SubtleColor,
            modifier = Modifier.size(20.dp))
    }
}

@Composable
fun BrandMark() {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(KickGreen), contentAlignment = Alignment.Center) {
        Text("P", color = AppBackground, fontWeight = FontWeight.Black, fontSize = 27.sp)
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Int = 36) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = KickGreen)
        if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun ErrorCard(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f), shape = RoundedCornerShape(12.dp)) {
        Text(message, modifier = Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 19.sp)
    }
}


package app.kickpocket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kickpocket.chat.ChatBuffer
import app.kickpocket.chat.KickChatClient
import app.kickpocket.data.ChannelInput
import app.kickpocket.data.ChatConnection
import app.kickpocket.data.ChatMessage
import app.kickpocket.data.Emote
import app.kickpocket.data.EmoteMenu
import app.kickpocket.data.KickChannel
import app.kickpocket.data.RecentChannel
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class StreamState(
    val channel: KickChannel? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val emotes: Map<String, Emote> = emptyMap(),
    val emoteWarnings: List<String> = emptyList(),
)

data class ChatSendState(val sending: Boolean = false, val error: String? = null, val sent: Long = 0)

class StreamViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PuntApplication
    private val repository = app.repository
    val preferences = app.preferences.preferences
    val updates = app.updates
    val player = app.playback
    private val mutable = app.streamState
    val state = mutable.asStateFlow()
    private val mutableMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = mutableMessages.asStateFlow()
    private val mutableConnection = MutableStateFlow(ChatConnection.Disconnected)
    val connection = mutableConnection.asStateFlow()
    val account = app.session.state
    private val follows = FollowController(viewModelScope, app.session::isFollowing, app.session::setFollowing)
    val followState = follows.state

    fun toggleFollow() = follows.toggle()
    fun retryFollow() = follows.refresh()

    private fun selectFollowTarget() {
        val channel = mutable.value.channel
        val accountId = account.value.account?.id
        follows.select(if (channel != null && !channel.isDemo && accountId != null && accountId != channel.userId)
            FollowTarget(channel.slug, accountId) else null)
    }
    private val mutableSend = MutableStateFlow(ChatSendState())
    val sendState = mutableSend.asStateFlow()
    private var sendJob: Job? = null

    fun send(content: String) {
        val channel = mutable.value.channel ?: return
        if (channel.isDemo || mutableSend.value.sending || content.isBlank()) return
        val requestGeneration = generation
        val accountId = account.value.account?.id
        val messageEmotes = mutable.value.emotes
        mutableSend.update { it.copy(sending = true, error = null) }
        sendJob = viewModelScope.launch {
            try {
                app.session.send(channel.chatroomId, content, messageEmotes)
                if (generation == requestGeneration) mutableSend.update { it.copy(sending = false, sent = it.sent + 1) }
                if (account.value.account?.id == accountId) runCatching { app.preferences.recordEmotes(content, messageEmotes) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == requestGeneration) mutableSend.update { it.copy(sending = false, error = error.message ?: "Could not send. Your message is still in the composer.") }
            }
        }
    }

    private var loadJob: Job? = null
    private var emoteJob: Job? = null
    private var emoteRevision = 0L
    private var chatJob: Job? = null
    private var refreshJob: Job? = null
    private var buffer = ChatBuffer()
    @Volatile private var foreground = false
    @Volatile private var generation = 0L

    init {
        viewModelScope.launch {
            var previousAccountId: Long? = null
            account.collect {
                selectFollowTarget()
                if (it.account?.id != previousAccountId) {
                    previousAccountId = it.account?.id
                    emoteRevision++
                    emoteJob?.cancel()
                    mutable.update { state -> state.copy(emotes = EmoteMenu.withoutAccount(state.emotes)) }
                    mutable.value.channel?.takeUnless { channel -> channel.isDemo }?.let { channel ->
                        val requestGeneration = generation
                        emoteJob = viewModelScope.launch { loadEmotes(channel, requestGeneration) }
                    }
                }
            }
        }
        // Network callbacks reduce messages off the main thread. Publish at most 10 times/sec.
        viewModelScope.launch {
            var lastBuffer = buffer
            var version = -1L
            while (isActive) {
                val current = buffer
                val (nextVersion, snapshot) = current.snapshot()
                if (lastBuffer !== current || version != nextVersion) {
                    mutableMessages.value = snapshot
                    version = nextVersion
                    lastBuffer = current
                }
                delay(100)
            }
        }
    }

    fun open(input: String) {
        val slug = try { ChannelInput.normalize(input) } catch (error: IllegalArgumentException) {
            mutable.update { it.copy(error = error.message) }
            return
        }
        reset()
        val requestGeneration = generation
        mutable.value = StreamState(loading = true)
        loadJob = viewModelScope.launch {
            try {
                val channel = repository.channel(slug)
                if (generation != requestGeneration) return@launch
                mutable.value = StreamState(channel = channel)
                selectFollowTarget()
                app.preferences.remember(channel)
                if (channel.isLive) channel.playbackUrl?.let { player.load(it, true, channel.title, channel.displayName) }
                startChat()
                startRefresh()
                emoteJob = viewModelScope.launch { loadEmotes(channel, requestGeneration) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(loading = false, error = readableError(error)) }
            }
        }
    }

    fun leave() { reset(); mutable.value = StreamState() }

    private fun reset() {
        generation++
        emoteRevision++
        follows.select(null)
        sendJob?.cancel(); sendJob = null
        mutableSend.value = ChatSendState()
        loadJob?.cancel()
        emoteJob?.cancel()
        chatJob?.cancel()
        refreshJob?.cancel()
        loadJob = null
        emoteJob = null
        chatJob = null
        refreshJob = null
        player.stop()
        buffer = ChatBuffer()
        mutableMessages.value = emptyList()
        mutableConnection.value = ChatConnection.Disconnected
    }

    fun foreground(active: Boolean) {
        foreground = active
        player.foreground(active)
        if (active) { startChat(); startRefresh() }
        else {
            chatJob?.cancel(); chatJob = null
            refreshJob?.cancel(); refreshJob = null
            if (mutable.value.channel != null) mutableConnection.value = ChatConnection.Paused
        }
    }

    private fun startChat() {
        val channel = mutable.value.channel ?: return
        if (!foreground || chatJob?.isActive == true) return
        if (channel.isDemo) {
            mutableConnection.value = ChatConnection.Connected
            return
        }
        val sessionBuffer = buffer
        val requestGeneration = generation
        chatJob = viewModelScope.launch(Dispatchers.IO) {
            KickChatClient(repository.http).collect(channel.chatroomId,
                onState = { if (generation == requestGeneration && foreground) mutableConnection.value = it },
                onEvent = sessionBuffer::apply,
                channelId = channel.id,
            )
        }
    }

    private fun startRefresh() {
        val channel = mutable.value.channel ?: return
        if (!foreground || channel.isDemo || refreshJob?.isActive == true) return
        val requestGeneration = generation
        refreshJob = viewModelScope.launch {
            var refreshCount = 0
            while (isActive) {
                delay(60_000)
                try {
                    refreshChannel(retryPlayback = false)
                    if (++refreshCount % 5 == 0) loadEmotes(channel, requestGeneration)
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* Keep playback/chat when a metadata refresh fails. */ }
            }
        }
    }

    fun retryPlayback() {
        val channel = mutable.value.channel ?: return
        if (channel.isDemo) { channel.playbackUrl?.let { player.load(it, false) }; return }
        if (loadJob?.isActive == true) return
        val requestGeneration = generation
        loadJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try { refreshChannel(retryPlayback = true) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = readableError(error)) } }
            finally { if (generation == requestGeneration) mutable.update { it.copy(loading = false) } }
        }
    }

    private suspend fun refreshChannel(retryPlayback: Boolean) {
        val old = mutable.value.channel ?: return
        val requestGeneration = generation
        val channel = repository.channel(old.slug)
        if (generation != requestGeneration) return
        mutable.update { it.copy(channel = channel) }
        if (channel.chatroomId != old.chatroomId) {
            chatJob?.cancel(); chatJob = null; startChat()
        }
        if (channel.isLive && (!old.isLive || retryPlayback)) channel.playbackUrl?.let { player.load(it, true, channel.title, channel.displayName) }
        else if (!channel.isLive && old.isLive) player.stop()
    }

    private suspend fun loadEmotes(channel: KickChannel, requestGeneration: Long) {
        val revision = ++emoteRevision
        val accountId = account.value.account?.id
        fun current() = generation == requestGeneration && emoteRevision == revision && account.value.account?.id == accountId
        val catalog = repository.emotes(channel)
        if (!current()) return
        mutable.update { it.copy(emotes = catalog.emotes, emoteWarnings = catalog.warnings) }
        if (accountId == null) return
        try {
            val owned = app.session.emotes(channel.slug)
            if (current()) mutable.update { it.copy(emotes = EmoteMenu.merge(catalog.emotes, owned)) }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            if (current()) mutable.update { it.copy(emoteWarnings = catalog.warnings + "Account emotes are unavailable. Reopen the channel to retry.") }
        }
    }

    fun animatedEmotes(enabled: Boolean) = app.preferences.update { it.copy(animatedEmotes = enabled) }
    fun fontSize(size: Int) = app.preferences.update { it.copy(chatFontSize = size) }
    fun clearRecent() = app.preferences.update { it.copy(recentChannels = emptyList()) }
    fun toggleFavorite(channel: RecentChannel) = app.preferences.toggleFavorite(channel)
    fun wifiQuality(height: Int) = app.preferences.update { it.copy(wifiQuality = height) }
    fun mobileQuality(height: Int) = app.preferences.update { it.copy(mobileQuality = height) }

    private fun readableError(error: Exception): String = when (error) {
        is IOException -> error.message ?: "Could not connect. Check your internet connection and retry."
        else -> "Could not open this channel. Try again later."
    }
}


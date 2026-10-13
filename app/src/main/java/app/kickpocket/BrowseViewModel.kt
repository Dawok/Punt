package app.kickpocket

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kickpocket.data.BrowseSort
import app.kickpocket.data.CategoryCard
import app.kickpocket.data.ChannelCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BrowseTab(val label: String) { Live("Live"), Categories("Categories"), Following("Following") }
data class BrowseState(
    val tab: BrowseTab = BrowseTab.Live,
    val category: CategoryCard? = null,
    val sort: BrowseSort = BrowseSort.Viewers,
    val channels: List<ChannelCard> = emptyList(),
    val categories: List<CategoryCard> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class BrowseViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as PuntApplication
    val account = app.session.state
    private val mutable = MutableStateFlow(BrowseState(tab = if (app.session.hasSavedSession) BrowseTab.Following else BrowseTab.Live))
    val state = mutable.asStateFlow()
    private val mutableFollowed = MutableStateFlow<List<ChannelCard>>(emptyList())
    val followedChannels = mutableFollowed.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch { app.session.restore() }
        viewModelScope.launch {
            var previousRevision = app.session.followingRevision.value
            app.session.followingRevision.collect { revision ->
                if (revision != previousRevision) {
                    previousRevision = revision
                    if (mutable.value.tab == BrowseTab.Following) {
                        loadJob?.cancel()
                        mutable.update { it.copy(channels = emptyList(), cursor = null) }
                        refresh()
                    }
                }
            }
        }
        viewModelScope.launch {
            var previousId: Long? = null
            account.collect { state ->
                if (state.account?.id != previousId) {
                    mutableFollowed.value = emptyList()
                    previousId = state.account?.id
                    if (state.account != null) {
                        loadJob?.cancel()
                        mutable.value = BrowseState(tab = BrowseTab.Following, sort = mutable.value.sort)
                        refresh()
                    } else {
                        loadJob?.cancel()
                        mutable.value = BrowseState(tab = BrowseTab.Live)
                        refresh()
                    }
                }
            }
        }
    }

    fun select(tab: BrowseTab) {
        if (tab == mutable.value.tab && mutable.value.category == null) return
        loadJob?.cancel()
        mutable.value = BrowseState(tab = tab, sort = mutable.value.sort)
        refresh()
    }

    fun category(category: CategoryCard) {
        loadJob?.cancel()
        mutable.value = BrowseState(tab = BrowseTab.Categories, category = category, sort = mutable.value.sort)
        refresh()
    }

    fun backToCategories() { loadJob?.cancel(); mutable.value = BrowseState(tab = BrowseTab.Categories, sort = mutable.value.sort); refresh() }
    fun sort(sort: BrowseSort) {
        if (sort == mutable.value.sort) return
        loadJob?.cancel()
        mutable.value = BrowseState(tab = mutable.value.tab, category = mutable.value.category, sort = sort)
        refresh()
    }

    fun refresh() { loadJob?.cancel(); load(more = false) }
    fun more() { if (!mutable.value.loading && mutable.value.cursor != null) load(more = true) }
    fun checkLogin(reportMissingSession: Boolean = false) { viewModelScope.launch { app.session.refresh(reportMissingSession) } }
    fun signOut() = app.session.signOut()

    private fun load(more: Boolean) {
        val old = mutable.value
        if ((old.tab == BrowseTab.Following || old.sort == BrowseSort.Recommended) && account.value.account == null) {
            mutable.value = old.copy(channels = emptyList(), cursor = null, loading = false, error = null)
            return
        }
        mutable.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val cursor = if (more) old.cursor else null
                if (old.tab == BrowseTab.Categories && old.category == null) {
                    val page = if (old.sort == BrowseSort.Recommended) app.session.recommendedCategories() else app.repository.categories(cursor)
                    mutable.update { it.copy(categories = ((if (more) old.categories else emptyList()) + page.items).distinctBy { item -> item.id },
                        cursor = page.cursor.takeUnless { value -> value == cursor }, loading = false) }
                } else {
                    val page = when {
                        old.tab == BrowseTab.Following -> app.session.followed(cursor)
                        old.sort == BrowseSort.Recommended -> app.session.recommendedStreams(old.category, cursor)
                        else -> app.repository.streams(old.category, cursor)
                    }
                    mutable.update { it.copy(channels = ((if (more) old.channels else emptyList()) + page.items).distinctBy { item -> item.slug },
                        cursor = page.cursor.takeUnless { value -> value == cursor }, loading = false) }
                    if (old.tab == BrowseTab.Following) mutableFollowed.value = mutable.value.channels
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(loading = false, error = error.message ?: "Could not load channels. Try again.") } }
        }
    }
}

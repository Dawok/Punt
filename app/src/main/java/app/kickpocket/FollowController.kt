package app.kickpocket

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FollowState(
    val following: Boolean? = null,
    val checking: Boolean = false,
    val changing: Boolean = false,
    val error: String? = null,
)

internal data class FollowTarget(val slug: String, val accountId: Long)

/** Owns one channel/account pair; late responses cannot update a different pair. */
internal class FollowController(
    private val scope: CoroutineScope,
    private val read: suspend (String) -> Boolean,
    private val write: suspend (String, Boolean) -> Unit,
) {
    private val mutable = MutableStateFlow(FollowState())
    val state = mutable.asStateFlow()
    private var target: FollowTarget? = null
    private var revision = 0L
    private var job: Job? = null

    fun select(next: FollowTarget?) {
        if (next == target) return
        revision++
        job?.cancel()
        target = next
        mutable.value = FollowState()
        if (next != null) refresh()
    }

    fun refresh() {
        val current = target ?: return
        if (mutable.value.checking || mutable.value.changing) return
        val version = revision
        mutable.value = FollowState(checking = true)
        job = scope.launch {
            try {
                val following = read(current.slug)
                if (revision == version) mutable.value = FollowState(following = following)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (revision == version) mutable.value = FollowState(error = error.message ?: "Could not read your follow status. Try again.")
            }
        }
    }

    fun toggle() {
        val current = target ?: return
        val old = mutable.value
        if (old.checking || old.changing) return
        val following = old.following ?: return
        val version = revision
        mutable.value = old.copy(changing = true, error = null)
        job = scope.launch {
            try {
                write(current.slug, !following)
                if (revision == version) mutable.value = FollowState(following = !following)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                // A timeout may happen after Kick applied the change. Reload before another mutation.
                if (revision == version) mutable.value = FollowState(error = error.message ?: "Could not change your follow. Try again.")
            }
        }
    }
}

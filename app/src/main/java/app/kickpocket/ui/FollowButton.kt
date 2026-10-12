package app.kickpocket.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.kickpocket.FollowState

@Composable
fun FollowButton(
    state: FollowState,
    signedIn: Boolean,
    channelName: String,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onLogin: () -> Unit,
) {
    val busy = signedIn && (state.checking || state.changing)
    val retry = signedIn && state.following == null && !busy
    val following = signedIn && state.following == true
    val label = when {
        busy -> if (state.changing) "Saving…" else "Checking…"
        retry -> "Retry"
        following -> "Following"
        else -> "Follow"
    }
    val action = when {
        !signedIn -> "Sign in to follow $channelName"
        busy -> label
        retry -> "Retry follow status for $channelName"
        following -> "Unfollow $channelName"
        else -> "Follow $channelName"
    }
    Button(
        onClick = { if (!signedIn) onLogin() else if (retry) onRetry() else onToggle() },
        enabled = !busy,
        modifier = Modifier.widthIn(min = 100.dp).semantics {
            contentDescription = action
            stateDescription = if (following) "Following" else if (signedIn && state.following == false) "Not following" else "Follow status unknown"
        },
        colors = if (following || retry) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        }
        Text(label, maxLines = 1)
    }
}

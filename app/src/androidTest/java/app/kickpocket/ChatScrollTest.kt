package app.kickpocket

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import app.kickpocket.data.AppPreferences
import app.kickpocket.data.ChatConnection
import app.kickpocket.data.ChatMessage
import app.kickpocket.ui.ChatMessageList
import app.kickpocket.ui.PuntTheme
import org.junit.Rule
import org.junit.Test

class ChatScrollTest {
    @get:Rule val compose = createComposeRule()

    private fun message(index: Int) = ChatMessage("$index", 1, "viewer", null, "Message $index")
    private val messages = mutableStateOf((1..40).map(::message))

    private fun showChat() {
        compose.setContent {
            PuntTheme {
                ChatMessageList(messages.value, ChatConnection.Connected, emptyMap(), AppPreferences(),
                    Modifier.width(280.dp).height(240.dp))
            }
        }
    }

    private fun append(index: Int, bounded: Boolean = false) {
        compose.runOnIdle {
            messages.value = (messages.value + message(index)).let { if (bounded) it.takeLast(40) else it }
        }
        compose.waitForIdle()
    }

    @Test fun followsEveryNewMessageIncludingBatchesAndFullBuffer() {
        showChat()
        append(41)
        compose.onNodeWithText("Message 41", substring = true).assertIsDisplayed()
        compose.runOnIdle { messages.value = messages.value + (42..70).map(::message) }
        compose.onNodeWithText("Message 70", substring = true).assertIsDisplayed()
        append(71, bounded = true)
        append(72, bounded = true)
        compose.onNodeWithText("Message 72", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Jump to live").assertDoesNotExist()
    }

    @Test fun readingHistoryPausesFollowingAndScrollingBackDownResumesIt() {
        showChat()
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        compose.onNodeWithText("Jump to live").assertIsDisplayed()
        append(41)
        compose.onNodeWithText("Message 41", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Jump to live").assertIsDisplayed()

        // Move near the newest row, then finish with a real downward-to-live gesture.
        compose.onNodeWithTag("chat-messages").performScrollToIndex(2)
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeUp() }
        compose.onNodeWithText("Jump to live").assertDoesNotExist()
        append(42)
        compose.onNodeWithText("Message 42", substring = true).assertIsDisplayed()
    }

    @Test fun stoppingJustShortOfTheBottomAndJumpButtonBothResumeFollowing() {
        showChat()
        // Leave the newest row only slightly offset, without a fling.
        val distance = with(compose.density) { 32.dp.toPx() }
        compose.onNodeWithTag("chat-messages").performTouchInput {
            swipe(center, center + Offset(0f, distance), durationMillis = 1_000)
        }
        compose.onNodeWithText("Jump to live").assertDoesNotExist()
        append(41)
        compose.onNodeWithText("Message 41", substring = true).assertIsDisplayed()

        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        append(42)
        compose.onNodeWithText("Jump to live").performClick()
        compose.onNodeWithText("Message 42", substring = true).assertIsDisplayed()
        append(43)
        compose.onNodeWithText("Message 43", substring = true).assertIsDisplayed()
    }
}

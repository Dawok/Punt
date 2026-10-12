package app.kickpocket

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import app.kickpocket.data.Emote
import app.kickpocket.ui.ChatComposer
import app.kickpocket.ui.PuntTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EmotePickerSmokeTest {
    @get:Rule val compose = createComposeRule()
    private val image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aP9sAAAAASUVORK5CYII="
    private val catalog get() = mapOf(
        "Native" to Emote("kick:123", "Native", image),
        "Wink" to Emote("kick:124", "Wink", image),
        "Alias" to Emote("seven-tv-id", "Alias", image),
    )

    @Test fun selectionsOnlyEditDraftUntilSendAndFailedSendKeepsIt() {
        val send = mutableStateOf(ChatSendState())
        val requests = mutableListOf<String>()
        var pickerVisible = false
        compose.setContent {
            PuntTheme {
                ChatComposer("channel", true, false, catalog, false, send.value,
                    onSend = { requests += it; send.value = ChatSendState(sending = true) }, onLogin = {},
                    onPickerVisibility = { pickerVisible = it })
            }
        }
        compose.onNodeWithTag("chat-draft").performTextInput("hello")
        compose.onNodeWithContentDescription("Open emote menu").performClick()
        compose.waitForIdle()
        assertTrue(pickerVisible)
        compose.onNodeWithContentDescription("Insert Native").performClick()
        compose.onNodeWithTag("chat-draft").assertTextEquals("hello Native ")
        compose.onNodeWithContentDescription("Open emote menu").performClick()
        compose.onNodeWithContentDescription("Insert Alias").performClick()
        compose.onNodeWithTag("chat-draft").assertTextEquals("hello Native Alias ")
        compose.waitForIdle()
        assertFalse(pickerVisible)
        assertTrue(requests.isEmpty())
        compose.onNodeWithContentDescription("Send message").performClick()
        assertEquals(listOf("hello Native Alias "), requests)
        compose.onNodeWithContentDescription("Open emote menu").assertIsNotEnabled()
        compose.runOnIdle { send.value = ChatSendState(error = "Could not send") }
        compose.onNodeWithText("Could not send").assertIsDisplayed()
        compose.onNodeWithTag("chat-draft").assertTextEquals("hello Native Alias ")
        compose.runOnIdle { send.value = ChatSendState(sent = 1) }
        compose.onNodeWithTag("chat-draft").assert(hasText(""))
    }

    @Test fun filtersSearchAndSelectionReplacementUseChannelAliases() {
        compose.setContent {
            PuntTheme { ChatComposer("channel", true, false, catalog, false, ChatSendState(), {}, {}) }
        }
        compose.onNodeWithTag("chat-draft").performTextInput("one replace two")
        compose.onNodeWithTag("chat-draft").performTextInputSelection(TextRange(4, 11))
        compose.onNodeWithContentDescription("Open emote menu").performClick()
        compose.onNodeWithText("Kick").performClick()
        compose.onNodeWithContentDescription("Insert Alias").assertDoesNotExist()
        compose.onNodeWithText("Search emotes").performTextInput("nat")
        compose.onNodeWithText("Search emotes").performImeAction()
        compose.onNodeWithContentDescription("Insert Native").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert Wink").assertDoesNotExist()
        compose.onNodeWithContentDescription("Insert Native").performClick()
        compose.onNodeWithTag("chat-draft").assertTextEquals("one Native two")
        compose.onNodeWithContentDescription("Open emote menu").performClick()
        compose.onNodeWithText("7TV").performClick()
        compose.onNodeWithContentDescription("Insert Alias").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert Native").assertDoesNotExist()
        compose.onNodeWithText("Search emotes").performTextInput("missing")
        compose.onNodeWithText("No emotes match your search.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close emote menu").performClick()
        compose.onNodeWithTag("chat-draft").assertTextEquals("one Native two")
    }

    @Test fun fullDraftRejectsInsertionAndEmptyCatalogCanBeDismissed() {
        val emotes = mutableStateOf(catalog)
        compose.setContent {
            PuntTheme { ChatComposer("channel", true, false, emotes.value, false, ChatSendState(), {}, {}) }
        }
        val text = "x".repeat(500)
        compose.onNodeWithTag("chat-draft").performTextInput(text)
        compose.onNodeWithContentDescription("Open emote menu").performClick()
        compose.onNodeWithContentDescription("Insert Native").performClick()
        compose.onNodeWithTag("chat-draft").assertTextEquals(text)
        compose.onNodeWithText("Message is full. Remove some text to add this emote.").assertIsDisplayed()
        compose.runOnIdle { emotes.value = emptyMap() }
        compose.onNodeWithContentDescription("Open emote menu").performClick()
        compose.onNodeWithText("No emotes available yet.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close emote menu").performClick()
        compose.onNodeWithTag("chat-draft").assertTextEquals(text)
    }

    @Test fun restoredDraftIsNotClearedByAnEarlierSendAndChangingChannelResetsIt() {
        val restoration = StateRestorationTester(compose)
        val channel = mutableStateOf("channel")
        restoration.setContent {
            PuntTheme { ChatComposer(channel.value, true, false, catalog, false, ChatSendState(sent = 1), {}, {}) }
        }
        compose.onNodeWithTag("chat-draft").performTextInput("next message")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("chat-draft").assertTextEquals("next message")
        compose.runOnIdle { channel.value = "other" }
        compose.onNodeWithTag("chat-draft").assert(hasText(""))
    }
}

package app.kickpocket

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import app.kickpocket.data.Emote
import app.kickpocket.data.EmoteKind
import app.kickpocket.data.PreferenceStore
import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test fun ownedGroupsAndFrequentEmotesLeadTheMenuAndLockedEmotesAreHidden() {
        val ranked = mapOf(
            "Collect" to Emote("kick:1", "Collect", image, kind = EmoteKind.Collectible, preferred = true),
            "Sub" to Emote("kick:2", "Sub", image, kind = EmoteKind.Channel, preferred = true),
            "Locked" to Emote("kick:3", "Locked", image, kind = EmoteKind.Channel, usable = false),
            "Frequent" to Emote("7tv:1", "Frequent", image),
            "Rare" to Emote("7tv:2", "Rare", image),
        )
        compose.setContent {
            PuntTheme { ChatComposer("channel", true, false, ranked, false, ChatSendState(), {}, {},
                emoteUsage = mapOf("7tv:1|Frequent" to 10L, "7tv:2|Rare" to 1L)) }
        }
        compose.onNodeWithContentDescription("Open emote menu").performClick()
        compose.onNodeWithText("Collectibles").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert Collect").assertIsDisplayed()
        compose.onNodeWithText("Channel emotes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert Sub").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert Locked").assertDoesNotExist()
        compose.onNodeWithText("7TV").performClick()
        compose.onNodeWithText("Most used").assertIsDisplayed()
        val frequent = compose.onNodeWithContentDescription("Insert Frequent").fetchSemanticsNode().boundsInRoot
        val rare = compose.onNodeWithContentDescription("Insert Rare").fetchSemanticsNode().boundsInRoot
        assertTrue(frequent.top < rare.top || frequent.left < rare.left)
        compose.onNodeWithContentDescription("Insert Frequent").performClick()
        compose.onNodeWithTag("chat-draft").assertTextEquals("Frequent ")
    }

    @Test fun usagePreferencesSurviveOpeningANewStore() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("emote-usage-test", mode)
        }
        val storage = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        try {
            val store = PreferenceStore(context)
            store.recordEmotes("Alias Alias Native", catalog)
            val restored = PreferenceStore(context)
            assertEquals(2L, restored.preferences.value.emoteUsage["seven-tv-id|Alias"])
            assertEquals(1L, restored.preferences.value.emoteUsage["kick:123|Native"])
        } finally { storage.edit().clear().commit() }
    }

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

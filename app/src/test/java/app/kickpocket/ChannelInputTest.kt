package app.kickpocket

import app.kickpocket.data.ChannelInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChannelInputTest {
    @Test fun acceptsNamesAndKickLinks() {
        for (input in listOf(" user ", "@USER", "https://kick.com/user", "https://www.kick.com/user/?source=app", "kick.com/user")) {
            assertEquals("user", ChannelInput.normalize(input))
        }
    }

    @Test fun rejectsForeignHostsAndNonChannelPaths() {
        for (input in listOf("", "https://example.com/user", "https://kick.com.example.com/user", "https://kick.com/user/videos", "https://kick.com/", "user/name", "user?admin=true", "file://kick.com/user")) {
            assertThrows(IllegalArgumentException::class.java) { ChannelInput.normalize(input) }
        }
    }
}


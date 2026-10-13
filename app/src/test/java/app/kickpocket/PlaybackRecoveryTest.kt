package app.kickpocket

import app.kickpocket.player.PlaybackRecovery
import org.junit.Assert.*
import org.junit.Test

class PlaybackRecoveryTest {
    @Test fun pauseDoesNotConsumeRetriesAndFailuresEventuallyStop() {
        val recovery = PlaybackRecovery()
        assertNull(recovery.nextDelayMs(false))
        assertEquals(0, recovery.attempts)
        assertEquals(1_000L, recovery.nextDelayMs(true))
        assertNull(recovery.nextDelayMs(false))
        assertEquals(1, recovery.attempts)
        repeat(4) { assertNotNull(recovery.nextDelayMs(true)) }
        repeat(10) { assertNull(recovery.nextDelayMs(true)) }
        assertEquals(5, recovery.attempts)
        recovery.reset()
        assertEquals(1_000L, recovery.nextDelayMs(true))
    }
}

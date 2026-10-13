package app.kickpocket.player

/** One retry budget per uninterrupted playback attempt, reset after sustained progress. */
class PlaybackRecovery {
    var attempts: Int = 0
        private set

    fun nextDelayMs(playWhenReady: Boolean): Long? {
        if (!playWhenReady || attempts >= 5) return null
        return listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)[attempts++]
    }

    fun reset() { attempts = 0 }
}

object QualityPreferences {
    val choices = listOf(0, 240, 360, 480, 720, 1080, 1440, 2160)
    fun label(height: Int) = if (height <= 0) "Auto" else "${height}p"
    fun limit(height: Int) = if (height in 1..4320) height else Int.MAX_VALUE
}

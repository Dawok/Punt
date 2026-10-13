package app.kickpocket.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.kickpocket.MainActivity
import app.kickpocket.PuntApplication
import app.kickpocket.StreamState

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val playback get() = (application as PuntApplication).playback

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, playback.player).setSessionActivity(openApp).build()
        addSession(requireNotNull(session))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        session.takeIf { controllerInfo.isTrusted || controllerInfo.packageName == packageName }

    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as PuntApplication).streamState.value = StreamState()
        playback.stop()
        stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        playback.release()
        super.onDestroy()
    }
}

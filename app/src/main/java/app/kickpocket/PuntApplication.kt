package app.kickpocket

import android.app.Application
import android.content.Context
import app.kickpocket.data.KickRepository
import app.kickpocket.data.PreferenceStore
import app.kickpocket.data.KickSession
import app.kickpocket.player.PlayerController
import app.kickpocket.update.AppUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath

class PuntApplication : Application(), SingletonImageLoader.Factory {
    val repository by lazy { KickRepository() }
    val preferences by lazy { PreferenceStore(this) }
    val session by lazy { KickSession(this) }
    // Shared with the media service so playback does not depend on the activity lifecycle.
    val playback by lazy { PlayerController(this, preferences) }
    val streamState = MutableStateFlow(StreamState())
    val updates by lazy { AppUpdater(this, preferences, repository.http) }

    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(AnimatedImageDecoder.Factory())
            add(OkHttpNetworkFetcherFactory(repository.http))
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .diskCache {
            DiskCache.Builder().directory(context.cacheDir.resolve("emotes").toOkioPath())
                .maxSizeBytes(128L * 1024 * 1024).build()
        }
        .build()
}


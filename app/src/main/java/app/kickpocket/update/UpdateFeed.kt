package app.kickpocket.update

import app.kickpocket.data.AppJson
import java.io.IOException
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resumeWithException

@Serializable
data class UpdateManifest(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String)
data class AppUpdate(val versionCode: Long, val versionName: String, val apkUrl: String, val sha256: String)

object UpdateFeed {
    fun parse(raw: String, source: String, installedVersion: Long): AppUpdate? {
        val base = source.toHttpUrl()
        require(base.isHttps && base.username.isEmpty() && base.password.isEmpty()) { "Use an HTTPS update source." }
        val manifest = AppJson.decodeFromString<UpdateManifest>(raw)
        require(manifest.versionCode in 1..2_100_000_000L && manifest.versionName.isNotBlank()) { "Invalid update version." }
        require(manifest.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "Invalid update checksum." }
        val apk = base.resolve(manifest.apkUrl) ?: error("Invalid APK download link.")
        require(apk.isHttps && apk.username.isEmpty() && apk.password.isEmpty()) { "Use an HTTPS APK download." }
        if (manifest.versionCode <= installedVersion) return null
        return AppUpdate(manifest.versionCode, manifest.versionName, apk.toString(), manifest.sha256.lowercase())
    }
}

class UpdateDownloader(private val http: OkHttpClient) {
    suspend fun check(source: String, installedVersion: Long): AppUpdate? = withContext(Dispatchers.IO) {
        require(source.toHttpUrl().isHttps) { "Use an HTTPS update source." }
        http.newCall(Request.Builder().url(source).header("Cache-Control", "no-cache").build()).awaitResponse().use { response ->
            require(response.request.url.isHttps) { "The update source must stay on HTTPS." }
            val body = response.body ?: throw IOException("The update source returned no data.")
            val buffered = body.source()
            require(!buffered.request(64 * 1024 + 1L)) { "The update feed is too large." }
            val bytes = buffered.readByteArray()
            UpdateFeed.parse(bytes.toString(Charsets.UTF_8), source, installedVersion)
        }
    }

    suspend fun download(update: AppUpdate, target: File, onProgress: (Int?) -> Unit) = withContext(Dispatchers.IO) {
        val partial = File(target.parentFile, "${target.name}.part")
        target.parentFile?.mkdirs()
        try {
            http.newCall(Request.Builder().url(update.apkUrl).build()).awaitResponse().use { response ->
                require(response.request.url.isHttps) { "The APK download must stay on HTTPS." }
                val body = response.body ?: throw IOException("The download was empty.")
                val length = body.contentLength()
                require(length <= 250L * 1024 * 1024) { "The update file is too large." }
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            require(received <= 250L * 1024 * 1024) { "The update file is too large." }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                            val percent = if (length > 0) ((received * 100 / length).toInt()).coerceAtMost(100) else null
                            if (percent == null || percent != lastPercent) { onProgress(percent); lastPercent = percent ?: -1 }
                        }
                    }
                }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == update.sha256) { "The download could not be verified. Please try again." }
                require(received > 0 && (length < 0 || received == length)) { "The download was incomplete." }
                check(partial.renameTo(target)) { "Could not save the downloaded update." }
            }
        } finally { partial.delete() }
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                if (continuation.isActive) continuation.resumeWithException(IOException(
                    if (code == 404 || code == 401 || code == 403) "Updates are not available from this source. The repository may still be private."
                    else "Could not reach the update source. Try again later."))
            } else continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

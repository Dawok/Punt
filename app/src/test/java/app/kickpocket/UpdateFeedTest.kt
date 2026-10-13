package app.kickpocket

import app.kickpocket.update.*
import app.kickpocket.data.AppJson
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class UpdateFeedTest {
    private val hash = "a".repeat(64)
    private fun manifest(code: Long = 12, url: String = "punt.apk", checksum: String = hash) =
        AppJson.encodeToString(UpdateManifest(code, "0.6.3", url, checksum))

    @Test fun resolvesRelativeDownloadsAndDoesNotOfferOlderOrSameVersions() {
        val source = "https://example.com/updates/update.json"
        assertEquals("https://example.com/updates/punt.apk", UpdateFeed.parse(manifest(), source, 11)!!.apkUrl)
        assertNull(UpdateFeed.parse(manifest(11), source, 11))
        assertNull(UpdateFeed.parse(manifest(10), source, 11))
    }

    @Test fun rejectsInsecureSourcesDownloadsAndMalformedChecksums() {
        for ((raw, source) in listOf(manifest() to "http://example.com/update.json",
            manifest(url = "http://example.com/punt.apk") to "https://example.com/update.json",
            manifest(checksum = "invalid") to "https://example.com/update.json")) {
            try { UpdateFeed.parse(raw, source, 11); fail("Expected rejection") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun checksAndDownloadsOverHttpsAndRejectsACorruptedFile() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        val http = OkHttpClient.Builder().sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager).build()
        val directory = Files.createTempDirectory("update-test-").toFile()
        try {
            val bytes = "apk-fixture".toByteArray()
            val checksum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            server.enqueue(MockResponse().setBody(manifest(checksum = checksum)))
            val downloader = UpdateDownloader(http)
            val update = downloader.check(server.url("/update.json").toString(), 11)!!
            server.enqueue(MockResponse().setBody("apk-fixture"))
            val target = File(directory, "punt.apk")
            downloader.download(update, target) {}
            assertArrayEquals(bytes, target.readBytes())
            server.enqueue(MockResponse().setBody("corrupted-file"))
            val badTarget = File(directory, "bad.apk")
            try { downloader.download(update, badTarget) {}; fail("Expected checksum rejection") }
            catch (_: IllegalArgumentException) { }
            assertFalse(badTarget.exists())
            assertFalse(File(directory, "bad.apk.part").exists())
        } finally {
            directory.deleteRecursively()
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }
}

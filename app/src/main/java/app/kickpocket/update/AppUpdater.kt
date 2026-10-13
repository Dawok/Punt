package app.kickpocket.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.kickpocket.BuildConfig
import app.kickpocket.data.PreferenceStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient

data class UpdateState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Int? = null,
    val available: AppUpdate? = null,
    val ready: Boolean = false,
    val checked: Boolean = false,
    val error: String? = null,
)

class AppUpdater(private val context: Context, private val preferences: PreferenceStore, http: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val downloader = UpdateDownloader(http.newBuilder().callTimeout(5, TimeUnit.MINUTES).build())
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var readyFile: File? = null
    private var awaitingInstallPermission = false
    val configured get() = BuildConfig.UPDATE_MANIFEST_URL.isNotBlank()

    fun checkIfDue() {
        if (configured && System.currentTimeMillis() - preferences.preferences.value.lastUpdateCheckMs >= 6 * 60 * 60 * 1_000L) check()
    }

    fun check() {
        if (job?.isActive == true) return
        if (!configured) { mutable.update { it.copy(error = "Updates are not configured for this build.") }; return }
        job = scope.launch {
            mutable.update { it.copy(checking = true, error = null) }
            preferences.update { it.copy(lastUpdateCheckMs = System.currentTimeMillis()) }
            try {
                val available = downloader.check(BuildConfig.UPDATE_MANIFEST_URL, BuildConfig.VERSION_CODE.toLong())
                if (available != mutable.value.available) readyFile = null
                mutable.update { it.copy(checking = false, checked = true, available = available, ready = readyFile?.isFile == true) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(checking = false, error = error.message ?: "Could not check for updates.") } }
        }
    }

    fun download() {
        val update = mutable.value.available ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            val file = File(context.cacheDir, "updates/punt-${update.versionCode}.apk")
            mutable.update { it.copy(downloading = true, progress = null, ready = false, error = null) }
            try {
                downloader.download(update, file) { progress -> mutable.update { it.copy(progress = progress) } }
                withContext(Dispatchers.IO) { verifyPackage(file, update) }
                readyFile = file
                mutable.update { it.copy(downloading = false, ready = true) }
            } catch (error: CancellationException) { file.delete(); mutable.update { it.copy(downloading = false) }; throw error }
            catch (error: Exception) { file.delete(); mutable.update { it.copy(downloading = false, error = error.message ?: "Could not download the update.") } }
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyPackage(file: File, update: AppUpdate) {
        val manager = context.packageManager
        val candidate = manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: error("This download is not a valid Android app.")
        require(candidate.packageName == context.packageName && candidate.longVersionCode == update.versionCode) {
            "This download cannot update the installed app."
        }
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val existing = installed.signingInfo?.apkContentsSigners?.toSet().orEmpty()
        val signing = candidate.signingInfo ?: error("This update could not be verified.")
        val compatible = if (signing.hasMultipleSigners() || existing.size != 1)
            existing == signing.apkContentsSigners.toSet()
        else signing.signingCertificateHistory?.toSet().orEmpty().containsAll(existing)
        require(existing.isNotEmpty() && compatible) { "This download cannot update the installed app. It was signed with a different key." }
    }

    fun cancelDownload() { job?.cancel() }

    fun install(activity: Context) {
        val file = readyFile ?: return
        try {
            if (!context.packageManager.canRequestPackageInstalls()) {
                awaitingInstallPermission = true
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
                return
            }
            awaitingInstallPermission = false
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { mutable.update { it.copy(error = "Could not open Android's installer. Please try again.") } }
    }

    fun resumeInstallIfAllowed(activity: Context) {
        if (awaitingInstallPermission && context.packageManager.canRequestPackageInstalls()) install(activity)
    }
}

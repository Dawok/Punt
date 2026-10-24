package app.kickpocket

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.verify.domain.DomainVerificationManager
import android.net.Uri
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test

class KickLinkIntentTest {
    @Test fun androidRegistersChannelVodAndClipLinksForBothKickHosts() {
        val app = ApplicationProvider.getApplicationContext<PuntApplication>()
        for (host in listOf("kick.com", "www.kick.com")) {
            for (path in listOf("channel", "channel/videos/12345678-1234-1234-1234-123456789abc", "channel/clips/clip_ABC", "channel?clip=clip_ABC")) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://$host/$path"))
                    .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(app.packageName)
                @Suppress("DEPRECATION")
                val activities = app.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                assertTrue(path, activities.any { it.activityInfo.name == MainActivity::class.java.name })
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            val domains = app.getSystemService(DomainVerificationManager::class.java)
                .getDomainVerificationUserState(app.packageName)?.hostToStateMap.orEmpty()
            assertTrue(domains.keys.containsAll(setOf("kick.com", "www.kick.com")))
        }
    }
}

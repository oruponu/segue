package com.oruponu.segue

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

private val APP_DOMAINS = setOf("root", "file", "database", "sharedpref", "external")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRulesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun backupIsDisabled() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }

    @Test
    fun deviceTransferExcludesAppData() {
        assertEquals(APP_DOMAINS, excludedDomains("device-transfer"))
    }

    @Test
    fun cloudBackupExcludesAppData() {
        assertEquals(APP_DOMAINS, excludedDomains("cloud-backup"))
    }

    private fun excludedDomains(section: String): Set<String> {
        val parser = context.resources.getXml(R.xml.data_extraction_rules)
        val domains = mutableSetOf<String>()
        var inSection = false
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG ->
                    when {
                        parser.name == section -> inSection = true
                        inSection && parser.name == "exclude" ->
                            domains += parser.getAttributeValue(null, "domain")
                    }
                XmlPullParser.END_TAG -> if (parser.name == section) inSection = false
            }
        }
        return domains
    }
}

package tr.borsatakip.v5.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Credentials and journals must not migrate independently of the device Keystore. */
class BackupExclusionPolicyTest {
    private val source = sequenceOf(File("src/main"), File("app/src/main"), File("android-v6323/app/src/main"))
        .first { File(it, "AndroidManifest.xml").isFile }
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")

    private fun parse(path: String): Element = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }.newDocumentBuilder().parse(File(source, path)).documentElement

    private fun assertExcluded(mode: Element) {
        assertEquals(0, mode.getElementsByTagName("include").length)
        val exclusions = mode.getElementsByTagName("exclude")
        val actual = (0 until exclusions.length).map { i ->
            val rule = exclusions.item(i) as Element
            assertEquals("Entire domain must be excluded", ".", rule.getAttribute("path"))
            rule.getAttribute("domain")
        }.toSet()
        assertEquals(domains, actual)
    }

    @Test fun legacyBackupExcludesAllAppData() {
        assertExcluded(parse("res/xml/backup_rules.xml"))
    }

    @Test fun cloudAndDeviceTransferBothExcludePrivateState() {
        val root = parse("res/xml/data_extraction_rules.xml")
        for (name in listOf("cloud-backup", "device-transfer")) {
            val modes = root.getElementsByTagName(name)
            assertEquals(1, modes.length)
            assertExcluded(modes.item(0) as Element)
        }
    }

    @Test fun manifestDisablesBackupAndReferencesBothPolicies() {
        val app = parse("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("false", app.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(android, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(android, "dataExtractionRules"))
    }
}

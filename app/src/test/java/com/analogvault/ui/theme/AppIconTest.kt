package com.analogvault.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The icons and the manifest have to agree: AppIcon names each alias by a
 * string, and nothing at compile time checks it exists. A wrong one would
 * leave the launcher with no icon for the app at all.
 */
class AppIconTest {

    private val ns = "http://schemas.android.com/apk/res/android"

    /** The manifest's aliases, by full class name. Gradle runs unit tests from the module dir. */
    private val aliases: Map<String, Element> by lazy {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = f.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val nodes = doc.getElementsByTagName("activity-alias")
        (0 until nodes.length).map { nodes.item(it) as Element }
            .associateBy { "com.analogvault" + it.getAttributeNS(ns, "name") }
    }

    @Test fun `every palette has one icon, and every icon an alias of its own`() {
        assertEquals(Palette.entries.toSet(), AppIcon.entries.map { it.palette }.toSet())
        assertEquals(AppIcon.entries.size, AppIcon.entries.map { it.alias }.toSet().size)
        assertEquals(AppIcon.entries.map { it.alias }.toSet(), aliases.keys)
    }

    @Test fun `each alias is a launcher entry for the one activity, in its palette's icon`() {
        for (icon in AppIcon.entries) {
            val a = aliases.getValue(icon.alias)
            assertEquals(icon.alias, ".VaultActivity", a.getAttributeNS(ns, "targetActivity"))
            assertEquals(icon.alias, "true", a.getAttributeNS(ns, "exported"))
            val categories = a.getElementsByTagName("category")
            assertTrue(icon.alias, (0 until categories.length).any {
                (categories.item(it) as Element).getAttributeNS(ns, "name") == "android.intent.category.LAUNCHER"
            })
            val expected = if (icon == AppIcon.DEFAULT) "@mipmap/ic_launcher" else "@mipmap/ic_launcher_${icon.palette.key}"
            assertEquals(icon.alias, expected, a.getAttributeNS(ns, "icon"))
            assertTrue("${icon.alias} icon file", File("src/main/res/mipmap-anydpi-v26/${expected.removePrefix("@mipmap/")}.xml").exists())
        }
    }

    @Test fun `only the default is on out of the box, under the activity's old name`() {
        assertEquals("com.analogvault.MainActivity", AppIcon.DEFAULT.alias)
        val enabled = aliases.filterValues { it.getAttributeNS(ns, "enabled") != "false" }.keys
        assertEquals(setOf(AppIcon.DEFAULT.alias), enabled)
    }
}

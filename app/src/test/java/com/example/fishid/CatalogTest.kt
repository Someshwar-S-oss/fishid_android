package com.example.fishid

import com.example.fishid.data.Catalog
import com.example.fishid.ml.ModelInfo
import com.example.fishid.ml.validateModel
import com.example.fishid.data.primaryName
import com.example.fishid.data.secondaryName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs against the real bundled data, so a malformed species.json fails the build. */
class CatalogTest {
    private val catalog = Catalog.load { File("src/main/assets", it).inputStream() }

    @Test fun loadsBundledData() {
        val bream = catalog.entry("scolopsis_bimaculata", "en")!!
        assertEquals("Thumbprint Monocle Bream", bream.commonName)
        assertEquals(9, bream.fieldMarks.size)
        assertEquals(400, bream.species.priceInrPerKg)
        assertNull(catalog.entry("no_such_fish", "en"))
    }

    @Test fun tamilFallsBackToEnglishTextAndLeadsWithLocalName() {
        val ta = catalog.entry("scolopsis_bimaculata", "ta")!!
        assertEquals("Thumbprint Monocle Bream", ta.commonName) // text.ta.json is empty: English fallback
        assertEquals(ta.species.localNameTa, ta.primaryName(tamil = true))
        assertEquals("Thumbprint Monocle Bream", ta.secondaryName(tamil = true))
        assertEquals(ta.species.localNameTa, ta.secondaryName(tamil = false))
    }

    /** Nutrition and price are optional (FishBase has neither); fin counts don't apply to the crab. */
    @Test fun everyBundledSpeciesHasCoreFacts() {
        catalog.species.forEach {
            assertTrue("${it.id} field marks", catalog.entry(it.id, "en")!!.fieldMarks.isNotEmpty())
            assertTrue("${it.id} images", it.images.isNotEmpty())
            it.images.forEach { img -> assertTrue("missing $img", File("src/main/assets/${Catalog.IMAGE_DIR}/$img").isFile) }
            assertTrue("${it.id} meristics", it.meristics != null || it.id == "charybdis_natator")
        }
    }

    /** A dropped-in model.json whose labels aren't in species.json (or needs newer data) fails here, not on a phone. */
    @Test fun bundledModelMatchesBundledData() {
        val info = Catalog.json.decodeFromString<ModelInfo>(File("src/main/assets/model/model.json").readText())
        val problem = validateModel(info, info.labels.size, catalog.species.map { it.id }.toSet(), appVersion = 1, dataVersion = catalog.dataVersion)
        assertNull(problem, problem)
    }

    /** The admin site's single-file catalog: newer and keeping every species → accepted; otherwise the phone keeps its data. */
    @Test fun downloadedCatalogReplacesOnlyWhenNewerAndComplete() {
        fun remote(version: Int, ids: List<String>): Catalog {
            val species = ids.joinToString(",") { "{\"id\":\"$it\",\"scientific_name\":\"x\",\"price_inr_per_kg\":999}" }
            return Catalog.parse(
                """{"data_version":$version,"updated":"2026-11-05","species":[$species],"text":{"en":{"${ids[0]}":{"common_name":"Remote name"}}}}""",
            )
        }
        val ids = catalog.species.map { it.id }
        val next = remote(catalog.dataVersion + 1, ids + "new_fish")
        assertTrue(next.replaces(catalog))
        assertEquals("Remote name", next.entry(ids[0], "ta")!!.commonName)
        assertEquals(999, next.entry(ids[0], "en")!!.species.priceInrPerKg)
        assertFalse(remote(catalog.dataVersion, ids).replaces(catalog))
        assertFalse(remote(catalog.dataVersion + 1, ids.drop(1)).replaces(catalog))
    }

    @Test fun searchMatchesEveryNameInEveryScript() {
        val e = catalog.entry("scolopsis_bimaculata", "en")!!
        assertTrue(e.matches(""))
        assertTrue(e.matches("  monocle "))
        assertTrue(e.matches("SCOLOPSIS"))
        assertTrue(e.matches(e.species.localNameTa!!.take(3)))
        assertFalse(e.matches("mackerel"))
    }
}

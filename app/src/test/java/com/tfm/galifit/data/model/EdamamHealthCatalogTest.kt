package com.tfm.galifit.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdamamHealthCatalogTest {

    @Test
    fun labelsForConditions_hypertension_includesDashAndLowSodium() {
        val labels = EdamamHealthCatalog.labelsForConditions(listOf("hypertension"))
        assertTrue(labels.contains("DASH"))
        assertTrue(labels.contains("LOW_SODIUM"))
    }

    @Test
    fun resolveHealthLabels_mergesAllSourcesWithoutDuplicates() {
        val prefs = UserPreferences(
            dietType = "vegetarian",
            restrictions = listOf("GLUTEN_FREE"),
            healthConditions = listOf("celiac"),
            healthLabels = listOf("GLUTEN_FREE", "FODMAP_FREE")
        )
        val labels = EdamamHealthCatalog.resolveHealthLabels(prefs)
        assertEquals(1, labels.count { it == "GLUTEN_FREE" })
        assertTrue(labels.contains("VEGETARIAN"))
        assertTrue(labels.contains("WHEAT_FREE"))
        assertTrue(labels.contains("FODMAP_FREE"))

        assertTrue(labels.contains("ALCOHOL_FREE"))
    }

    @Test
    fun resolveHealthLabels_ketoDiet_addsKetoFriendly() {
        val labels = EdamamHealthCatalog.resolveHealthLabels(
            UserPreferences(dietType = "keto")
        )
        assertTrue(labels.contains("KETO_FRIENDLY"))
    }

    @Test
    fun resolveHealthLabels_paleoDiet_addsPaleo() {
        val labels = EdamamHealthCatalog.resolveHealthLabels(
            UserPreferences(dietType = "paleo")
        )
        assertTrue(labels.contains("PALEO"))
    }

    @Test
    fun additionalSelectableLabels_excludesDietDerivedIncludingPaleo() {
        assertTrue("KETO_FRIENDLY" !in EdamamHealthCatalog.additionalSelectableLabels)
        assertTrue("VEGAN" !in EdamamHealthCatalog.additionalSelectableLabels)
        assertTrue("PALEO" !in EdamamHealthCatalog.additionalSelectableLabels)
        assertTrue(EdamamHealthCatalog.additionalSelectableLabels.isEmpty())
    }

    @Test
    fun normalizeLegacyPaleoPreference_promotesWhenDietUnset() {
        val prefs = EdamamHealthCatalog.normalizeLegacyPaleoPreference(
            UserPreferences(dietType = "none", healthLabels = listOf("PALEO"))
        )
        assertEquals("paleo", prefs.dietType)
        assertTrue("PALEO" !in prefs.healthLabels)
    }

    @Test
    fun normalizeLegacyPaleoPreference_keepsExistingDietType() {
        val prefs = EdamamHealthCatalog.normalizeLegacyPaleoPreference(
            UserPreferences(dietType = "keto", healthLabels = listOf("PALEO", "DASH"))
        )
        assertEquals("keto", prefs.dietType)
        assertEquals(listOf("DASH"), prefs.healthLabels)
    }

    @Test
    fun labelDisplayName_returnsSpanishTranslation() {
        assertEquals("Sin gluten", EdamamHealthCatalog.labelDisplayName("GLUTEN_FREE"))
        assertEquals("Azúcar limitado", EdamamHealthCatalog.labelDisplayName("SUGAR_CONSCIOUS"))
    }

    @Test
    fun displayLabel_usesShortConditionNames() {
        val byKey = EdamamHealthCatalog.conditions.associateBy { it.key }
        assertEquals(
            "Hipertensión (Enfoque DASH, Bajo en sodio)",
            byKey.getValue("hypertension").displayLabel()
        )
        assertEquals(
            "Diabetes (Azúcar limitado, Bajo en azúcar)",
            byKey.getValue("diabetes").displayLabel()
        )
        assertEquals(
            "Celiaquía (Sin gluten, Sin trigo)",
            byKey.getValue("celiac").displayLabel()
        )
        assertEquals(
            "Digestivo (Sin FODMAP)",
            byKey.getValue("fodmap").displayLabel()
        )
        assertEquals("Apoyo inmunológico", byKey.getValue("immune").displayLabel())
    }
}

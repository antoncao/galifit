package com.tfm.galifit.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EdamamShoppingListModelsTest {

    @Test
    fun measureToDisplayUnit_mapsGram() {
        assertEquals("g", measureToDisplayUnit("http://www.edamam.com/ontologies/edamam.owl#Measure_gram"))
    }

    @Test
    fun toDisplayText_formatsWholeNumber() {
        val qty = IngredientQuantity(quantity = 2.0, measure = "http://www.edamam.com/ontologies/edamam.owl#Measure_unit")
        assertEquals("2 uds", qty.toDisplayText())
    }
}

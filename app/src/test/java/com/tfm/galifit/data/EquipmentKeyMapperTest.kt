package com.tfm.galifit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EquipmentKeyMapperTest {

    @Test
    fun keyFromWgerName_recognisesCommonEquipment() {
        assertEquals(EquipmentKeyMapper.DUMBBELL, EquipmentKeyMapper.keyFromWgerName("Dumbbell"))
        assertEquals(EquipmentKeyMapper.BARBELL, EquipmentKeyMapper.keyFromWgerName("Barbell"))
        assertEquals(EquipmentKeyMapper.PULL_UP_BAR, EquipmentKeyMapper.keyFromWgerName("Pull-up bar"))
    }

    @Test
    fun keyFromWgerName_unknownEquipment_returnsNull() {
        assertNull(EquipmentKeyMapper.keyFromWgerName("Cable machine"))
    }

    @Test
    fun keyFromExerciseDbEquipment_mapsTokensAndMachines() {
        assertEquals(EquipmentKeyMapper.BANDS, EquipmentKeyMapper.keyFromExerciseDbEquipment("band"))
        assertEquals(EquipmentKeyMapper.BARBELL, EquipmentKeyMapper.keyFromExerciseDbEquipment("ez-bar"))
        assertEquals(EquipmentKeyMapper.MACHINE, EquipmentKeyMapper.keyFromExerciseDbEquipment("cable"))
    }

    @Test
    fun keyFromExerciseDbEquipment_bodyweight_returnsNull() {
        assertNull(EquipmentKeyMapper.keyFromExerciseDbEquipment("bodyweight"))
        assertNull(EquipmentKeyMapper.keyFromExerciseDbEquipment(""))
    }

    @Test
    fun labelFor_knownKeyReturnsSpanish_unknownFallsBack() {
        assertEquals("Mancuernas", EquipmentKeyMapper.labelFor(EquipmentKeyMapper.DUMBBELL))
        assertEquals("Trx", EquipmentKeyMapper.labelFor("trx"))
    }
}

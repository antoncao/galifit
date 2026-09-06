package com.tfm.galifit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectiveMapperTest {

    @Test
    fun nutritionObjective_mapsLegacyKeysToUnifiedObjective() {
        assertEquals("lose_weight", ObjectiveMapper.nutritionObjective("lose_fat"))
        assertEquals("gain_muscle", ObjectiveMapper.nutritionObjective("gain_muscle"))
        assertEquals("maintain", ObjectiveMapper.nutritionObjective("stay_fit"))
    }

    @Test
    fun nutritionObjective_unknownKey_defaultsToMaintain() {
        assertEquals("maintain", ObjectiveMapper.nutritionObjective("cualquier_cosa"))
    }

    @Test
    fun trainingObjective_derivesFromBodyObjective() {
        assertEquals("fat_loss", ObjectiveMapper.trainingObjective("lose_weight"))
        assertEquals("hypertrophy", ObjectiveMapper.trainingObjective("gain_muscle"))
        assertEquals("mobility", ObjectiveMapper.trainingObjective("maintain"))
    }

    @Test
    fun needsWeeklyWeightGoal_onlyForLoseOrGain() {
        assertTrue(ObjectiveMapper.needsWeeklyWeightGoal("lose_weight"))
        assertTrue(ObjectiveMapper.needsWeeklyWeightGoal("gain_muscle"))
        assertFalse(ObjectiveMapper.needsWeeklyWeightGoal("maintain"))
    }

    @Test
    fun isMuscleGain_trueOnlyForGainMuscle() {
        assertTrue(ObjectiveMapper.isMuscleGain("gain_muscle"))
        assertFalse(ObjectiveMapper.isMuscleGain("lose_weight"))
    }
}

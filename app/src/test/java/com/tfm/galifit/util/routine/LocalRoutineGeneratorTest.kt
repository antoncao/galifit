package com.tfm.galifit.util.routine

import com.tfm.galifit.data.catalog.BodyRegion
import com.tfm.galifit.data.catalog.CatalogExercise
import com.tfm.galifit.data.catalog.ExerciseCatalog
import com.tfm.galifit.data.model.UserPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRoutineGeneratorTest {

    private fun bodyweightCatalog(): ExerciseCatalog {

        val regions = BodyRegion.entries.filter { it != BodyRegion.OTHER && it != BodyRegion.CARDIO }
        val exercises = regions.flatMap { region ->
            (1..4).map { i ->
                CatalogExercise(
                    id = "${region.name}_$i",
                    name = "${region.name} ejercicio $i",
                    region = region,
                    equipmentKeys = emptySet()
                )
            }
        }
        return ExerciseCatalog(source = "test", exercises = exercises)
    }

    @Test
    fun generate_alwaysReturnsSevenCalendarDays() = runBlocking {
        val prefs = UserPreferences(
            trainingDaysPerWeek = 3,
            trainingObjective = "hypertrophy",
            trainingSplit = "auto"
        )
        val plan = LocalRoutineGenerator().generate(prefs, bodyweightCatalog())
        assertEquals(7, plan.days.size)
    }

    @Test
    fun generate_restDaysComplementTrainingDays() = runBlocking {
        val prefs = UserPreferences(
            trainingDaysPerWeek = 4,
            trainingObjective = "hypertrophy",
            trainingSplit = "auto"
        )
        val plan = LocalRoutineGenerator().generate(prefs, bodyweightCatalog())
        val trainingDays = plan.days.count { !it.isRest }
        val restDays = plan.days.count { it.isRest }
        assertEquals(4, trainingDays)
        assertEquals(3, restDays)
    }

    @Test
    fun generate_trainingDaysHaveExercisesAndRestDaysDoNot() = runBlocking {
        val prefs = UserPreferences(
            trainingDaysPerWeek = 3,
            trainingObjective = "hypertrophy",
            trainingSplit = "auto"
        )
        val plan = LocalRoutineGenerator().generate(prefs, bodyweightCatalog())
        plan.days.forEach { day ->
            if (day.isRest) {
                assertTrue(day.exercises.isEmpty())
            } else {
                assertTrue("Un día de entrenamiento no debe quedar vacío", day.exercises.isNotEmpty())
            }
        }
    }

    @Test
    fun generate_populatesPlanSummaryAndLocalSource() = runBlocking {
        val prefs = UserPreferences(
            trainingDaysPerWeek = 5,
            trainingObjective = "strength",
            trainingSplit = "auto",
            trainingLevel = "advanced",
            trainingLocation = "gym"
        )
        val plan = LocalRoutineGenerator().generate(prefs, bodyweightCatalog())
        assertEquals(5, plan.planSummary.daysPerWeek)
        assertEquals("strength", plan.planSummary.objective)
        assertTrue(plan.source.contains("local_generator"))
    }

    @Test
    fun generate_upperDayCoversAllFocusRegions() = runBlocking {

        val prefs = UserPreferences(
            trainingDaysPerWeek = 4,
            trainingObjective = "hypertrophy",
            trainingSplit = "upper_lower"
        )
        val plan = LocalRoutineGenerator().generate(prefs, bodyweightCatalog())
        val monday = plan.days.first { !it.isRest }
        val regionsCovered = monday.exercises
            .mapNotNull { it.catalogExerciseId?.substringBefore('_') }
            .toSet()
        listOf(BodyRegion.CHEST, BodyRegion.BACK, BodyRegion.SHOULDERS, BodyRegion.ARMS)
            .forEach { region ->
                assertTrue(
                    "El día de tren superior debe incluir ${region.name}",
                    regionsCovered.contains(region.name)
                )
            }
    }

    @Test
    fun generate_isReproducibleForSameSeed() = runBlocking {
        val prefs = UserPreferences(
            trainingDaysPerWeek = 4,
            trainingObjective = "hypertrophy",
            trainingSplit = "auto"
        )
        val fixedClock = { 0L }
        val planA = LocalRoutineGenerator(fixedClock).generate(prefs, bodyweightCatalog())
        val planB = LocalRoutineGenerator(fixedClock).generate(prefs, bodyweightCatalog())
        val idsA = planA.days.flatMap { it.exercises.mapNotNull { e -> e.catalogExerciseId } }
        val idsB = planB.days.flatMap { it.exercises.mapNotNull { e -> e.catalogExerciseId } }
        assertEquals(idsA, idsB)
    }

    @Test
    fun generate_kneeLimitationExcludesLegExercises() = runBlocking {
        val prefs = UserPreferences(
            trainingDaysPerWeek = 6,
            trainingObjective = "hypertrophy",
            trainingSplit = "full_body",
            trainingLimitations = listOf("knee")
        )
        val plan = LocalRoutineGenerator().generate(prefs, bodyweightCatalog())
        val hasLegWork = plan.days
            .flatMap { it.exercises }
            .any { it.catalogExerciseId?.startsWith(BodyRegion.LEGS.name) == true ||
                it.catalogExerciseId?.startsWith(BodyRegion.CALVES.name) == true }
        assertTrue("Con limitación de rodilla no deben aparecer ejercicios de pierna/gemelo", !hasLegWork)
    }
}

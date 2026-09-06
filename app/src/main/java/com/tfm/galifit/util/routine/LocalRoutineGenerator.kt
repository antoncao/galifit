package com.tfm.galifit.util.routine

import com.tfm.galifit.data.catalog.BodyRegion
import com.tfm.galifit.data.catalog.CatalogExercise
import com.tfm.galifit.data.catalog.ExerciseCatalog
import com.tfm.galifit.data.model.FirebaseDayExercisePlan
import com.tfm.galifit.data.model.FirebaseSavedExercise
import com.tfm.galifit.data.model.FirebaseSavedExercisePlan
import com.tfm.galifit.data.model.PlanSummary
import com.tfm.galifit.data.model.UserPreferences
import com.tfm.galifit.util.GalifitFlowLog
import kotlin.random.Random

class LocalRoutineGenerator(

    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() }
) : ExerciseRoutineGenerator {

    override suspend fun generate(
        prefs: UserPreferences,
        catalog: ExerciseCatalog
    ): FirebaseSavedExercisePlan {
        val tGen0 = System.currentTimeMillis()
        val days = prefs.trainingDaysPerWeek.coerceIn(1, 7)
        val split = effectiveSplit(prefs.trainingSplit, days)
        val params = paramsFor(prefs.trainingObjective)
        val rng = Random(stableSeed(prefs))

        val weekDayNames = listOf(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
        )
        val trainingDayIndices = pickTrainingDayIndices(days)
        val focusSchedule = buildFocusSchedule(split, days)

        val userKeys = prefs.availableEquipment.toSet()
        val allowMachines = prefs.trainingLocation.equals("gym", true)
        val excludedRegions = limitationRegions(prefs.trainingLimitations)
        val maxExercisesPerDay = maxExercisesPerSession(
            prefs.trainingSessionMinutes,
            params.exercisesPerDay
        )
        GalifitFlowLog.api(
            "⏱ Gen: prep=${System.currentTimeMillis() - tGen0}ms split=$split days=$days " +
                "equipos=${userKeys.size} gym=$allowMachines limitaciones=${excludedRegions.size}"
        )

        val tPick0 = System.currentTimeMillis()
        val firebaseDays = weekDayNames.mapIndexed { idx, name ->
            val isTraining = idx in trainingDayIndices
            val day = FirebaseDayExercisePlan().apply {
                dayIndex = idx.toLong()
                dayName = name
                isRest = !isTraining
                focus = if (isTraining) {
                    val pos = trainingDayIndices.indexOf(idx)
                    focusSchedule.getOrNull(pos)?.label ?: "Entrenamiento"
                } else {
                    "Descanso"
                }
            }
            if (isTraining) {
                val pos = trainingDayIndices.indexOf(idx)
                val focus = focusSchedule[pos]
                day.exercises = pickExercisesForDay(
                    focus = focus,
                    catalog = catalog,
                    userKeys = userKeys,
                    allowMachines = allowMachines,
                    excludedRegions = excludedRegions,
                    rng = rng,
                    target = maxExercisesPerDay,
                    params = params
                )
            }
            day
        }
        GalifitFlowLog.api(
            "⏱ Gen: pickExercises 7d=${System.currentTimeMillis() - tPick0}ms " +
                "ejTot=${firebaseDays.sumOf { it.exercises.size }}"
        )

        val plan = FirebaseSavedExercisePlan().apply {
            createdAtMillis = System.currentTimeMillis()
            source = "${catalog.source}_local_generator"
            planSummary = PlanSummary().apply {
                daysPerWeek = days
                this.split = split
                objective = prefs.trainingObjective
                level = prefs.trainingLevel
                location = prefs.trainingLocation
            }
            this.days = firebaseDays
        }
        GalifitFlowLog.api("⏱ Gen: FIN total=${System.currentTimeMillis() - tGen0}ms")
        return plan
    }

    private fun effectiveSplit(requested: String, days: Int): String {
        val req = requested.ifBlank { "auto" }
        val auto = when {
            days <= 3 -> "full_body"
            days == 4 -> "upper_lower"
            days >= 5 -> "push_pull_legs"
            else -> "full_body"
        }
        return when (req) {
            "auto" -> auto
            "bro_split" -> if (days >= 5) "bro_split" else auto
            "push_pull_legs" -> if (days >= 3) "push_pull_legs" else auto
            "upper_lower" -> if (days >= 2) "upper_lower" else auto
            "full_body" -> "full_body"
            else -> auto
        }
    }

    private fun pickTrainingDayIndices(days: Int): List<Int> = when (days) {
        1 -> listOf(0)
        2 -> listOf(0, 3)
        3 -> listOf(0, 2, 4)
        4 -> listOf(0, 1, 3, 4)
        5 -> listOf(0, 1, 2, 4, 5)
        6 -> listOf(0, 1, 2, 3, 4, 5)
        7 -> listOf(0, 1, 2, 3, 4, 5, 6)
        else -> emptyList()
    }

    private fun buildFocusSchedule(split: String, days: Int): List<DayFocus> {
        val full = DayFocus(
            "Cuerpo completo",
            listOf(BodyRegion.LEGS, BodyRegion.BACK, BodyRegion.CHEST, BodyRegion.SHOULDERS, BodyRegion.ARMS, BodyRegion.ABS)
        )
        val upper = DayFocus("Tren superior", listOf(BodyRegion.CHEST, BodyRegion.BACK, BodyRegion.SHOULDERS, BodyRegion.ARMS))
        val lower = DayFocus("Tren inferior", listOf(BodyRegion.LEGS, BodyRegion.CALVES, BodyRegion.ABS))
        val push = DayFocus("Empuje", listOf(BodyRegion.CHEST, BodyRegion.SHOULDERS, BodyRegion.ARMS))
        val pull = DayFocus("Tirón", listOf(BodyRegion.BACK, BodyRegion.ARMS))
        val legs = DayFocus("Pierna", listOf(BodyRegion.LEGS, BodyRegion.CALVES, BodyRegion.ABS))
        val chestTri = DayFocus("Pecho y tríceps", listOf(BodyRegion.CHEST, BodyRegion.ARMS))
        val backBi = DayFocus("Espalda y bíceps", listOf(BodyRegion.BACK, BodyRegion.ARMS))
        val shouldersAbs = DayFocus("Hombro y core", listOf(BodyRegion.SHOULDERS, BodyRegion.ABS))

        return when (split) {
            "full_body" -> List(days) { full }
            "upper_lower" -> List(days) { i -> if (i % 2 == 0) upper else lower }
            "push_pull_legs" -> List(days) { i ->
                when (i % 3) { 0 -> push; 1 -> pull; else -> legs }
            }
            "bro_split" -> {
                val seq = listOf(chestTri, backBi, legs, shouldersAbs, full, full, full)
                List(days) { seq[it % seq.size] }
            }
            else -> List(days) { full }
        }
    }

    private fun limitationRegions(limitations: List<String>): Set<BodyRegion> {
        if (limitations.isEmpty()) return emptySet()
        val toExclude = mutableSetOf<BodyRegion>()
        limitations.forEach { lim ->
            when (lim) {
                "back" -> toExclude.add(BodyRegion.BACK)
                "knee" -> {
                    toExclude.add(BodyRegion.LEGS)
                    toExclude.add(BodyRegion.CALVES)
                }
                "shoulder" -> toExclude.add(BodyRegion.SHOULDERS)
            }
        }
        return toExclude
    }

    private fun maxExercisesPerSession(minutes: Int, baseline: Int): Int {
        val factor = when {
            minutes <= 30 -> 0.6
            minutes <= 45 -> 0.85
            minutes <= 60 -> 1.0
            else -> 1.2
        }
        return (baseline * factor).toInt().coerceIn(3, 8)
    }

    private fun pickExercisesForDay(
        focus: DayFocus,
        catalog: ExerciseCatalog,
        userKeys: Set<String>,
        allowMachines: Boolean,
        excludedRegions: Set<BodyRegion>,
        rng: Random,
        target: Int,
        params: ObjectiveParams
    ): List<FirebaseSavedExercise> {
        val activeRegions = focus.regions.filterNot { it in excludedRegions }

        val poolsByRegion = activeRegions.map { region ->
            catalog.exercises
                .filter { it.region == region }
                .filter { it.isAvailableFor(userKeys, allowMachines) }
                .shuffled(rng)
        }

        val selected = mutableListOf<CatalogExercise>()
        val seen = mutableSetOf<String>()
        val cursors = IntArray(poolsByRegion.size)

        var progressed = true
        while (selected.size < target && progressed) {
            progressed = false
            for (i in poolsByRegion.indices) {
                if (selected.size >= target) break
                val pool = poolsByRegion[i]
                while (cursors[i] < pool.size) {
                    val candidate = pool[cursors[i]]
                    cursors[i]++
                    if (seen.add(candidate.id)) {
                        selected.add(candidate)
                        progressed = true
                        break
                    }
                }
            }
        }

        if (selected.size < target) {
            val bodyweight = catalog.exercises
                .filter { it.requiresNoEquipment && it.id !in seen }
                .filter { it.region !in excludedRegions }
                .shuffled(rng)
                .take(target - selected.size)
            selected.addAll(bodyweight)
        }

        return selected.take(target).map { it.toSavedExercise(params) }
    }

    private fun CatalogExercise.toSavedExercise(params: ObjectiveParams): FirebaseSavedExercise {
        val detailsLine = "${params.sets} series x ${params.repsLow}-${params.repsHigh} reps · " +
            "${params.restSeconds}s descanso"

        return FirebaseSavedExercise().apply {
            name = this@toSavedExercise.name
            details = detailsLine

            thumbnailImageUrl = gifUrl
            catalogExerciseId = id
            wgerExerciseId = id.toLongOrNull()
            muscleGroup = this@toSavedExercise.muscleGroup
            equipmentLabel = this@toSavedExercise.equipmentLabel
            sets = params.sets.toLong()
            repsLow = params.repsLow.toLong()
            repsHigh = params.repsHigh.toLong()
            restSeconds = params.restSeconds.toLong()
            instructions = this@toSavedExercise.instructions
        }
    }

    private fun stableSeed(prefs: UserPreferences): Long {
        val today = nowMillisProvider() / (1000 * 60 * 60 * 24)
        val base = (prefs.trainingObjective + prefs.trainingSplit + prefs.trainingDaysPerWeek)
            .hashCode().toLong()
        return base xor today
    }

    private fun paramsFor(objective: String): ObjectiveParams = when (objective) {
        "strength" -> ObjectiveParams(sets = 5, repsLow = 3, repsHigh = 5, restSeconds = 180, exercisesPerDay = 5)
        "hypertrophy" -> ObjectiveParams(sets = 4, repsLow = 8, repsHigh = 12, restSeconds = 75, exercisesPerDay = 6)
        "endurance" -> ObjectiveParams(sets = 3, repsLow = 15, repsHigh = 20, restSeconds = 45, exercisesPerDay = 5)
        "fat_loss" -> ObjectiveParams(sets = 3, repsLow = 12, repsHigh = 15, restSeconds = 30, exercisesPerDay = 6)
        "mobility" -> ObjectiveParams(sets = 2, repsLow = 10, repsHigh = 15, restSeconds = 30, exercisesPerDay = 5)
        else -> ObjectiveParams(sets = 4, repsLow = 8, repsHigh = 12, restSeconds = 75, exercisesPerDay = 5)
    }

    private data class DayFocus(val label: String, val regions: List<BodyRegion>)

    private data class ObjectiveParams(
        val sets: Int,
        val repsLow: Int,
        val repsHigh: Int,
        val restSeconds: Int,
        val exercisesPerDay: Int
    )
}

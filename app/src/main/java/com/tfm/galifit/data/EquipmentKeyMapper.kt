package com.tfm.galifit.data

object EquipmentKeyMapper {

    const val DUMBBELL = "dumbbell"

    const val BARBELL = "barbell"

    const val BENCH = "bench"

    const val KETTLEBELL = "kettlebell"

    const val BANDS = "bands"

    const val SWISS_BALL = "swiss_ball"

    const val PULL_UP_BAR = "pull_up_bar"

    const val MAT = "mat"

    const val MACHINE = "machine"

    val LABELS_ES: Map<String, String> = linkedMapOf(
        DUMBBELL to "Mancuernas",
        BARBELL to "Barra",
        BENCH to "Banco",
        KETTLEBELL to "Kettlebell",
        BANDS to "Bandas elásticas",
        SWISS_BALL to "Fitball",
        PULL_UP_BAR to "Barra de dominadas",
        MAT to "Esterilla"
    )

    val SELECTABLE_EQUIPMENT_KEYS: List<String> = LABELS_ES.keys.toList()

    fun keyFromWgerName(name: String): String? {
        val n = name.lowercase().trim()
        return when {
            "dumbbell" in n -> DUMBBELL
            n == "barbell" || "barbell" in n && "sz" !in n -> BARBELL
            "bench" in n -> BENCH
            "kettlebell" in n -> KETTLEBELL
            "band" in n -> BANDS
            "swiss" in n || "ball" in n && "med" !in n -> SWISS_BALL
            "pull-up" in n || "pull up" in n -> PULL_UP_BAR
            "mat" in n -> MAT
            else -> null
        }
    }

    fun labelFor(key: String): String = LABELS_ES[key] ?: key.replaceFirstChar { it.uppercase() }

    fun keyFromExerciseDbEquipment(raw: String): String? = when (raw.lowercase().trim()) {
        "band" -> BANDS
        "barbell", "ez-bar" -> BARBELL
        "dumbbell" -> DUMBBELL
        "kettlebell" -> KETTLEBELL
        "cable", "lever", "machine", "smith", "sled" -> MACHINE
        "bodyweight", "other", "" -> null
        else -> null
    }

    fun labelFromExerciseDbEquipment(raw: String): String = when (raw.lowercase().trim()) {
        "band" -> "Banda elástica"
        "barbell" -> "Barra"
        "ez-bar" -> "Barra Z"
        "bodyweight" -> "Peso corporal"
        "cable" -> "Polea"
        "dumbbell" -> "Mancuernas"
        "kettlebell" -> "Kettlebell"
        "lever", "machine" -> "Máquina"
        "smith" -> "Multipower"
        "sled" -> "Trineo"
        "other" -> "Otro"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}

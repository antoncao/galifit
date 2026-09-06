package com.tfm.galifit.data.catalog

import android.content.Context
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.api.WgerService
import com.tfm.galifit.data.EquipmentKeyMapper
import com.tfm.galifit.data.model.WgerEquipment
import com.tfm.galifit.data.model.WgerExerciseInfo
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WgerCatalogProvider(
    context: Context,
    private val service: WgerService = ApiClient().getWger().create(WgerService::class.java),
    maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
) : CachedExerciseCatalogProvider(context, maxAgeMillis) {

    override val cacheFileName: String = "wger_catalog.json"
    override val sourceTag: String = ExerciseCatalogProviderFactory.SOURCE_WGER

    override suspend fun download(): ExerciseCatalog = withContext(Dispatchers.IO) {
        GalifitFlowLog.api("⏱ [wger] DL: inicio (idioma → equipment → category → muscle → exercises)")

        val tLang0 = System.currentTimeMillis()
        val languages = service.listLanguages().results
        GalifitFlowLog.api("⏱ [wger] DL: listLanguages=${System.currentTimeMillis() - tLang0}ms (${languages.size})")

        val languageId = languages.firstOrNull { it.short_name.equals("es", true) }?.id
            ?: languages.firstOrNull { it.short_name.equals("en", true) }?.id
            ?: 2

        val tEq0 = System.currentTimeMillis()
        val equipment = service.listEquipment().results
        GalifitFlowLog.api("⏱ [wger] DL: listEquipment=${System.currentTimeMillis() - tEq0}ms (${equipment.size})")

        val tCat0 = System.currentTimeMillis()
        val categories = service.listCategories().results
        GalifitFlowLog.api("⏱ [wger] DL: listCategories=${System.currentTimeMillis() - tCat0}ms (${categories.size})")

        val categoryNameById = categories.associate { it.id to it.name }

        val allExercises = mutableListOf<WgerExerciseInfo>()
        var offset = 0
        val pageSize = 100
        val maxPages = 12
        var pages = 0
        val tExAll0 = System.currentTimeMillis()
        while (pages < maxPages) {
            val tPage0 = System.currentTimeMillis()
            val page = service.listExercises(limit = pageSize, offset = offset)
            GalifitFlowLog.api(
                "⏱ [wger] DL: listExercises offset=$offset size=${page.results.size} " +
                    "en ${System.currentTimeMillis() - tPage0}ms (next=${page.next != null})"
            )
            allExercises.addAll(page.results)
            pages++
            if (page.next.isNullOrBlank() || page.results.size < pageSize) break
            offset += pageSize
        }
        GalifitFlowLog.api(
            "⏱ [wger] DL: exercises TOTAL=${System.currentTimeMillis() - tExAll0}ms páginas=$pages crudos=${allExercises.size}"
        )

        val tMap0 = System.currentTimeMillis()
        val resolved = allExercises.mapNotNull { ex ->
            val byLang = ex.translations.firstOrNull { it.language == languageId }
            val fallbackEn = ex.translations.firstOrNull { it.language == 2 }
            val any = byLang ?: fallbackEn ?: ex.translations.firstOrNull() ?: return@mapNotNull null
            val name = any.name.trim()
            if (name.isBlank()) return@mapNotNull null

            val categoryName = ex.category?.name ?: categoryNameById[ex.category?.id] ?: ""
            val (equipmentKeys, equipmentLabel) = mapEquipment(ex.equipment)

            CatalogExercise(
                id = ex.id.toString(),
                name = name,
                muscleGroup = ex.muscles.firstOrNull()?.name?.takeIf { it.isNotBlank() }
                    ?: categoryName.takeIf { it.isNotBlank() },
                region = BodyRegion.fromWgerCategory(categoryName),
                equipmentKeys = equipmentKeys,
                equipmentLabel = equipmentLabel,
                gifUrl = null,
                instructions = any.description.take(400)
            )
        }
        GalifitFlowLog.api(
            "⏱ [wger] DL: normalizado=${System.currentTimeMillis() - tMap0}ms (${allExercises.size}→${resolved.size})"
        )

        ExerciseCatalog(
            source = sourceTag,
            exercises = resolved,
            cachedAtMillis = System.currentTimeMillis()
        )
    }

    private fun mapEquipment(equipment: List<WgerEquipment>): Pair<Set<String>, String> {
        val real = equipment.filterNot {
            val n = it.name.lowercase()
            "bodyweight" in n || "none" in n
        }
        if (real.isEmpty()) return emptySet<String>() to "Peso corporal"

        val keys = real.mapNotNull { EquipmentKeyMapper.keyFromWgerName(it.name) }.toMutableSet()
        if (keys.isEmpty()) keys.add(EquipmentKeyMapper.MACHINE)

        val label = real.joinToString(", ") { eq ->
            EquipmentKeyMapper.keyFromWgerName(eq.name)?.let(EquipmentKeyMapper::labelFor) ?: eq.name
        }.ifBlank { "Peso corporal" }
        return keys to label
    }
}

package com.tfm.galifit.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.tfm.galifit.data.model.CachedRecipeDetailPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object RecipeDetailDiskCache {

    private const val SUBDIR = "recipe_details"
    private val gson = Gson()

    private fun cacheDir(context: Context): File =
        File(context.filesDir, SUBDIR).apply { mkdirs() }

    private fun sha256Short(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun fileForRef(context: Context, recipeReferenceUrl: String): File {
        val key = recipeReferenceUrl.trim()
        return File(cacheDir(context), "detail_${sha256Short(key)}.json")
    }

    fun read(context: Context, recipeReferenceUrl: String): CachedRecipeDetailPayload? {
        val key = recipeReferenceUrl.trim()
        if (key.isEmpty()) return null
        val file = fileForRef(context, key)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val json = file.readText(Charsets.UTF_8)
            val payload = gson.fromJson(json, CachedRecipeDetailPayload::class.java)
            if (payload == null || !payload.isCompatible()) {
                null
            } else {
                GalifitFlowLog.disk("Detalle receta leído de disco: ${file.name}")
                payload
            }
        } catch (_: JsonSyntaxException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun write(context: Context, recipeReferenceUrl: String, payload: CachedRecipeDetailPayload) {
        val key = recipeReferenceUrl.trim()
        if (key.isEmpty()) return
        withContext(Dispatchers.IO) {
            try {
                val file = fileForRef(context, key)
                file.writeText(gson.toJson(payload), Charsets.UTF_8)
                GalifitFlowLog.disk("Detalle receta guardado en disco: ${file.name} (${file.length()} bytes)")
            } catch (e: Exception) {
                GalifitFlowLog.warn("No se pudo guardar caché de receta: ${e.message}")
            }
        }
    }
}

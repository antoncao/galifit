package com.tfm.galifit.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tfm.galifit.BuildConfig
import com.tfm.galifit.api.ApiClient
import com.tfm.galifit.api.DeepLService
import com.tfm.galifit.data.model.DeepLTranslateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object TranslationService {

    private const val TARGET_LANG = "ES"
    private const val SOURCE_LANG = "EN"
    private const val MAX_TEXTS_PER_REQUEST = 50
    private const val SUBDIR = "translations"
    private const val CACHE_FILE = "es.json"

    private val gson = Gson()
    private val memoryCache = ConcurrentHashMap<String, String>()
    private val diskMutex = Mutex()

    @Volatile
    private var diskLoaded = false

    private val apiKey: String
        get() = BuildConfig.DEEPL_API_KEY.trim()

    private val service: DeepLService? by lazy {
        if (apiKey.isEmpty()) null
        else ApiClient().getDeepL(apiKey).create(DeepLService::class.java)
    }

    fun isEnabled(): Boolean = apiKey.isNotEmpty()

    suspend fun translate(context: Context?, text: String): String {
        if (text.isBlank()) return text
        return translate(context, listOf(text)).firstOrNull() ?: text
    }

    suspend fun translate(context: Context?, texts: List<String>): List<String> {
        if (!isEnabled() || texts.isEmpty()) return texts
        ensureDiskLoaded(context)

        val result = arrayOfNulls<String>(texts.size)
        val missIndices = mutableListOf<Int>()
        val missTexts = mutableListOf<String>()

        texts.forEachIndexed { index, raw ->
            val key = raw.trim()
            when {
                key.isEmpty() -> result[index] = raw
                memoryCache.containsKey(key) -> result[index] = memoryCache[key]
                else -> {
                    missIndices.add(index)
                    missTexts.add(key)
                }
            }
        }

        if (missTexts.isNotEmpty()) {
            val translated = requestDeepL(missTexts.distinct())
            if (translated != null) {
                missIndices.forEachIndexed { i, originalIndex ->
                    val key = missTexts[i]
                    result[originalIndex] = memoryCache[key] ?: texts[originalIndex]
                }
                persistToDisk(context)
            } else {
                missIndices.forEach { originalIndex -> result[originalIndex] = texts[originalIndex] }
            }
        }

        return result.mapIndexed { index, value -> value ?: texts[index] }
    }

    private suspend fun requestDeepL(uniqueTexts: List<String>): Boolean? {
        val client = service ?: return null
        val pending = uniqueTexts.filterNot { memoryCache.containsKey(it) }
        if (pending.isEmpty()) return true

        var anyFailure = false
        for (chunk in pending.chunked(MAX_TEXTS_PER_REQUEST)) {
            val ok = withContext(Dispatchers.IO) {
                try {
                    GalifitFlowLog.api("DeepL: traduciendo ${chunk.size} textos EN→$TARGET_LANG")
                    val response = client.translate(
                        DeepLTranslateRequest(
                            text = chunk,
                            targetLang = TARGET_LANG,
                            sourceLang = SOURCE_LANG
                        )
                    )
                    if (!response.isSuccessful) {
                        GalifitFlowLog.warn("DeepL: HTTP ${response.code()} al traducir")
                        return@withContext false
                    }
                    val translations = response.body()?.translations
                    if (translations == null || translations.size != chunk.size) {
                        GalifitFlowLog.warn("DeepL: respuesta inesperada (${translations?.size} vs ${chunk.size})")
                        return@withContext false
                    }
                    chunk.forEachIndexed { i, original ->
                        val translatedText = translations[i].text.takeIf { it.isNotBlank() } ?: original
                        memoryCache[original] = translatedText
                    }
                    true
                } catch (e: Exception) {
                    GalifitFlowLog.warn("DeepL: excepción al traducir: ${e.message}")
                    false
                }
            }
            if (!ok) anyFailure = true
        }
        return if (anyFailure && pending.none { memoryCache.containsKey(it) }) null else true
    }

    private suspend fun ensureDiskLoaded(context: Context?) {
        if (diskLoaded || context == null) return
        diskMutex.withLock {
            if (diskLoaded) return
            withContext(Dispatchers.IO) {
                try {
                    val file = cacheFile(context)
                    if (file.exists() && file.length() > 0) {
                        val json = file.readText(Charsets.UTF_8)
                        val type = object : TypeToken<Map<String, String>>() {}.type
                        val map: Map<String, String>? = gson.fromJson(json, type)
                        map?.forEach { (k, v) -> memoryCache.putIfAbsent(k, v) }
                        GalifitFlowLog.disk("Traducciones cargadas de disco: ${map?.size ?: 0}")
                    }
                } catch (e: Exception) {
                    GalifitFlowLog.warn("No se pudo leer caché de traducciones: ${e.message}")
                }
            }
            diskLoaded = true
        }
    }

    private suspend fun persistToDisk(context: Context?) {
        if (context == null) return
        diskMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val snapshot = HashMap(memoryCache)
                    cacheFile(context).writeText(gson.toJson(snapshot), Charsets.UTF_8)
                } catch (e: Exception) {
                    GalifitFlowLog.warn("No se pudo guardar caché de traducciones: ${e.message}")
                }
            }
        }
    }

    private fun cacheFile(context: Context): File {
        val dir = File(context.filesDir, SUBDIR).apply { mkdirs() }
        return File(dir, CACHE_FILE)
    }
}

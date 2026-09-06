package com.tfm.galifit.data.catalog

import android.content.Context
import com.google.gson.Gson
import com.tfm.galifit.util.GalifitFlowLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

abstract class CachedExerciseCatalogProvider(

    protected val context: Context,
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS
) : ExerciseCatalogProvider {

    private val mutex = Mutex()
    private val gson = Gson()

    @Volatile
    private var inMemory: ExerciseCatalog? = null

    protected abstract val cacheFileName: String

    protected abstract val sourceTag: String

    protected abstract suspend fun download(): ExerciseCatalog

    final override suspend fun loadCatalog(forceRefresh: Boolean): ExerciseCatalog = mutex.withLock {
        val tStart = System.currentTimeMillis()

        inMemory?.takeIf { !forceRefresh && !isStale(it) }?.let {
            GalifitFlowLog.api(
                "⏱ [$sourceTag] loadCatalog: HIT memoria (${it.exercises.size} ej) en " +
                    "${System.currentTimeMillis() - tStart}ms"
            )
            return@withLock it
        }

        if (!forceRefresh) {
            val tDisk0 = System.currentTimeMillis()
            val disk = readFromDisk()
            GalifitFlowLog.api(
                "⏱ [$sourceTag] loadCatalog: readFromDisk=${System.currentTimeMillis() - tDisk0}ms " +
                    "(found=${disk != null})"
            )
            disk?.takeIf { !isStale(it) }?.let {
                inMemory = it
                GalifitFlowLog.api(
                    "⏱ [$sourceTag] loadCatalog: HIT disco (${it.exercises.size} ej), total=" +
                        "${System.currentTimeMillis() - tStart}ms"
                )
                return@withLock it
            }
        }

        GalifitFlowLog.api("⏱ [$sourceTag] loadCatalog: MISS, descargando catálogo…")
        val tDl0 = System.currentTimeMillis()
        val fresh = download()
        GalifitFlowLog.api(
            "⏱ [$sourceTag] loadCatalog: download total=${System.currentTimeMillis() - tDl0}ms " +
                "(${fresh.exercises.size} ej)"
        )

        val tWrite0 = System.currentTimeMillis()
        writeToDisk(fresh)
        GalifitFlowLog.api("⏱ [$sourceTag] loadCatalog: writeToDisk=${System.currentTimeMillis() - tWrite0}ms")

        inMemory = fresh
        GalifitFlowLog.api("⏱ [$sourceTag] loadCatalog: FIN total=${System.currentTimeMillis() - tStart}ms")
        fresh
    }

    private fun isStale(c: ExerciseCatalog): Boolean =
        c.source != sourceTag || System.currentTimeMillis() - c.cachedAtMillis > maxAgeMillis

    private fun cacheFile(): File = File(context.filesDir, cacheFileName)

    private suspend fun readFromDisk(): ExerciseCatalog? = withContext(Dispatchers.IO) {
        val f = cacheFile()
        if (!f.exists() || f.length() == 0L) return@withContext null
        val sizeKb = f.length() / 1024
        try {
            val tRead0 = System.currentTimeMillis()
            val text = f.readText(Charsets.UTF_8)
            val tRead = System.currentTimeMillis() - tRead0
            val tParse0 = System.currentTimeMillis()
            val parsed = gson.fromJson(text, ExerciseCatalog::class.java)
            val tParse = System.currentTimeMillis() - tParse0
            GalifitFlowLog.api("⏱ [$sourceTag] readFromDisk: ${sizeKb}KB, read=${tRead}ms, parse=${tParse}ms")
            parsed
        } catch (t: Throwable) {
            GalifitFlowLog.warn("[$sourceTag] catálogo: cache corrupta (${t.message}), se ignora")
            f.delete()
            null
        }
    }

    private suspend fun writeToDisk(catalog: ExerciseCatalog) = withContext(Dispatchers.IO) {
        try {
            val tJson0 = System.currentTimeMillis()
            val json = gson.toJson(catalog)
            val tJson = System.currentTimeMillis() - tJson0
            val tWrite0 = System.currentTimeMillis()
            cacheFile().writeText(json, Charsets.UTF_8)
            val tWrite = System.currentTimeMillis() - tWrite0
            GalifitFlowLog.api(
                "⏱ [$sourceTag] writeToDisk: ${json.length / 1024}KB, toJson=${tJson}ms, write=${tWrite}ms"
            )
        } catch (t: Throwable) {
            GalifitFlowLog.warn("[$sourceTag] catálogo: error escribiendo cache (${t.message})")
        }
    }

    companion object {

        const val DEFAULT_MAX_AGE_MILLIS = 30L * 24 * 60 * 60 * 1000
    }
}

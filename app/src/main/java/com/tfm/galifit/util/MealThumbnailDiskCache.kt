package com.tfm.galifit.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object MealThumbnailDiskCache {

    private const val SUBDIR = "meal_thumbnails"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    private fun cacheDir(context: Context): File =
        File(context.filesDir, SUBDIR).apply { mkdirs() }

    private fun fileForKey(context: Context, cacheKey: String): File {
        val hash = sha256Short(cacheKey)
        return File(cacheDir(context), "$hash.jpg")
    }

    private fun sha256Short(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    suspend fun ensureCached(context: Context, remoteUrl: String, cacheKey: String): String? {
        val trimmed = remoteUrl.trim()
        if (trimmed.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            try {
                val file = fileForKey(context, cacheKey)
                if (file.exists() && file.length() > 0L) {
                    GalifitFlowLog.disk(
                        "Miniatura en caché (lectura disco, sin descarga): ${file.name} (${file.length()} bytes)"
                    )
                    return@withContext file.absolutePath
                }
                GalifitFlowLog.api("Descargando miniatura desde red → guardar en disco: ${file.name}")
                val connection = (URL(trimmed).openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                }
                try {
                    connection.inputStream.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                } finally {
                    connection.disconnect()
                }
                if (file.exists() && file.length() > 0L) {
                    GalifitFlowLog.disk("Miniatura guardada en disco: ${file.absolutePath} (${file.length()} bytes)")
                    file.absolutePath
                } else {
                    GalifitFlowLog.warn("Miniatura: fichero vacío o no creado tras descarga")
                    null
                }
            } catch (e: Exception) {
                GalifitFlowLog.warn("Miniatura caché fallo: ${e.message}")
                null
            }
        }
    }
}

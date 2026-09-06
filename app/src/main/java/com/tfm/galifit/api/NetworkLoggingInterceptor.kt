package com.tfm.galifit.api

import android.util.Log
import com.tfm.galifit.util.GalifitFlowLog
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class NetworkLoggingInterceptor(
    private val tag: String = "HTTP_EXACT",
    private val maxBodyChars: Int = 200_000
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val t0 = System.nanoTime()

        val url = request.url.toString()
        val method = request.method
        GalifitFlowLog.api("Petición → $method ${url.take(160)}${if (url.length > 160) "…" else ""}")
        val headers = request.headers.toString().trimEnd()
        val contentType = request.body?.contentType()?.toString()
        val bodyString = request.body?.let { bodyToString(it) }

        Log.d(tag, "→ $method $url")
        if (headers.isNotBlank()) Log.d(tag, "→ Headers:\n$headers")
        if (!contentType.isNullOrBlank()) Log.d(tag, "→ Content-Type: $contentType")
        if (!bodyString.isNullOrBlank()) {
            Log.d(tag, "→ Body (${bodyString.length} chars):\n${bodyString.take(maxBodyChars)}")
            if (bodyString.length > maxBodyChars) {
                Log.d(tag, "→ Body truncated to $maxBodyChars chars")
            }
        } else {
            Log.d(tag, "→ Body: <empty>")
        }

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Log.e(tag, "✕ $method $url failed: ${e.message}", e)
            GalifitFlowLog.api("Error red ✕ $method: ${e.message}")
            throw e
        }

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)
        Log.d(tag, "← ${response.code} ${response.message} ($tookMs ms) $url")
        GalifitFlowLog.api("Respuesta ← ${response.code} (${tookMs}ms) $method ${url.take(120)}${if (url.length > 120) "…" else ""}")

        if (!response.isSuccessful || method == "POST" || Log.isLoggable(tag, Log.DEBUG)) {
            response.peekBody(64_000).string().takeIf { it.isNotBlank() }?.let { peek ->
                Log.d(tag, "← Body (${peek.length} chars, peek):\n${peek.take(maxBodyChars)}")
            }
        }

        return response
    }

    private fun bodyToString(body: RequestBody): String {
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            val charset: Charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
            buffer.readString(charset)
        } catch (e: Exception) {
            "<unable to read body: ${e.message}>"
        }
    }
}

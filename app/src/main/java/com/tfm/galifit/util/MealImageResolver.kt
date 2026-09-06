package com.tfm.galifit.util

import android.content.Context
import com.tfm.galifit.data.model.Meal
import java.io.File

object MealImageResolver {

    const val DRAWABLE_URI_PREFIX = "galifit-drawable:"

    fun drawableUri(drawableName: String): String = "$DRAWABLE_URI_PREFIX$drawableName"

    fun isLocalDrawableUri(value: String?): Boolean =
        value?.trim()?.startsWith(DRAWABLE_URI_PREFIX, ignoreCase = true) == true

    fun resolveDrawableResId(context: Context, uri: String): Int? {
        if (!isLocalDrawableUri(uri)) return null
        val name = uri.trim().removePrefix(DRAWABLE_URI_PREFIX).trim()
        if (name.isEmpty()) return null
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        return resId.takeIf { it != 0 }
    }

    fun resolveThumbnailLoadModel(context: Context, meal: Meal): Any? {
        val localFile = meal.thumbnailLocalPath?.let { File(it) }
        if (localFile != null && localFile.exists() && localFile.length() > 0) {
            return localFile
        }
        val thumbUrl = meal.thumbnailImageUrl?.trim().orEmpty()
        if (thumbUrl.isEmpty()) return null
        resolveDrawableResId(context, thumbUrl)?.let { return it }
        return thumbUrl
    }

    fun resolveDetailLoadModel(context: Context, detailImageUrl: String?): Any? {
        val url = detailImageUrl?.trim().orEmpty()
        if (url.isEmpty()) return null
        resolveDrawableResId(context, url)?.let { return it }
        return url
    }
}

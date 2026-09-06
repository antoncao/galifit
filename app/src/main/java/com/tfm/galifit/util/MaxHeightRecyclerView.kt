package com.tfm.galifit.util

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.core.content.res.use
import androidx.recyclerview.widget.RecyclerView
import com.tfm.galifit.R

class MaxHeightRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var maxHeightRatio: Float = DEFAULT_MAX_HEIGHT_RATIO
        set(value) {
            field = value.coerceIn(0.1f, 1f)
            requestLayout()
        }

    init {
        attrs?.let { attributeSet ->
            context.obtainStyledAttributes(
                attributeSet,
                R.styleable.MaxHeightRecyclerView
            ).use { typedArray ->
                maxHeightRatio = typedArray.getFloat(
                    R.styleable.MaxHeightRecyclerView_maxHeightRatio,
                    DEFAULT_MAX_HEIGHT_RATIO
                )
            }
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val maxHeightPx = (resources.displayMetrics.heightPixels * maxHeightRatio).toInt()
        super.onMeasure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST)
        )
    }

    private companion object {
        const val DEFAULT_MAX_HEIGHT_RATIO = 0.75f
    }
}

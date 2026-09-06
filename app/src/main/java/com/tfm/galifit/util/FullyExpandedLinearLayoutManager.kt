package com.tfm.galifit.util

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FullyExpandedLinearLayoutManager(
    context: Context,
    orientation: Int = VERTICAL,
    reverseLayout: Boolean = false
) : LinearLayoutManager(context, orientation, reverseLayout) {

    override fun onMeasure(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
        widthSpec: Int,
        heightSpec: Int
    ) {
        val widthSize = View.MeasureSpec.getSize(widthSpec)
        val widthMode = View.MeasureSpec.getMode(widthSpec)
        val heightMode = View.MeasureSpec.getMode(heightSpec)
        val heightSize = View.MeasureSpec.getSize(heightSpec)

        var totalHeight = 0
        val count = state.itemCount
        for (i in 0 until count) {
            val view = recycler.getViewForPosition(i)
            measureChildWithMargins(
                view,
                widthSpec,
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            totalHeight += getDecoratedMeasuredHeight(view)
            recycler.recycleView(view)
        }

        val resolvedHeight = when (heightMode) {
            View.MeasureSpec.EXACTLY -> heightSize
            View.MeasureSpec.AT_MOST -> totalHeight.coerceAtMost(heightSize)
            else -> totalHeight
        }
        setMeasuredDimension(
            View.MeasureSpec.makeMeasureSpec(widthSize, widthMode),
            resolvedHeight
        )
    }
}

package com.tfm.galifit.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.tfm.galifit.R

class ElaborationAdapter(
    private val steps: List<String>
) : RecyclerView.Adapter<ElaborationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val elaborationStep: TextView = view.findViewById(R.id.elaborationStep)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.meal_detail_elaboration, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = steps[position]
        val stepText = "${position + 1}. $step"

        holder.elaborationStep.text = stepText
        holder.elaborationStep.setOnLongClickListener { view ->
            copyStepToClipboard(view.context, stepText)
            true
        }
    }

    override fun getItemCount(): Int = steps.size

    private fun copyStepToClipboard(context: Context, stepText: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(CLIP_LABEL, stepText))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Paso copiado al portapapeles", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {

        const val CLIP_LABEL = "Elaboración"
    }
}

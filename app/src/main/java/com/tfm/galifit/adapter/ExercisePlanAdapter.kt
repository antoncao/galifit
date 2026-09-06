package com.tfm.galifit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tfm.galifit.R
import com.tfm.galifit.data.model.Exercise

class ExercisePlanAdapter(
    private val exercises: MutableList<Exercise>,
    private val onEditExercise: ((exercise: Exercise, position: Int) -> Unit)? = null,
    private val onDeleteExercise: ((exercise: Exercise, position: Int) -> Unit)? = null
) : RecyclerView.Adapter<ExercisePlanAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtExercise: TextView = view.findViewById(R.id.tvExerciseName)
        val txtReps: TextView = view.findViewById(R.id.tvExerciseReps)
        val imgExercise: ImageView = view.findViewById(R.id.imgExercise)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEdit)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)

        val exerciseItem: View = view.findViewById(R.id.layoutExercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.exercise_plan_item_exercise, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount() = exercises.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exercise = exercises[position]

        holder.txtExercise.text = exercise.name
        holder.imgExercise.contentDescription = exercise.name

        holder.txtReps.text = buildRepsLine(exercise)

        val img = exercise.thumbnailImageUrl
        if (!img.isNullOrBlank()) {
            Glide.with(holder.itemView.context)
                .load(img)
                .centerCrop()
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .into(holder.imgExercise)
        } else {
            Glide.with(holder.itemView.context).clear(holder.imgExercise)
            holder.imgExercise.setImageDrawable(null)
            holder.imgExercise.setBackgroundResource(R.drawable.bg_image_placeholder)
        }

        holder.btnEliminar.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val removed = exercises[pos]
                exercises.removeAt(pos)
                notifyItemRemoved(pos)
                onDeleteExercise?.invoke(removed, pos)
            }
        }

        holder.btnEdit.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onEditExercise?.invoke(exercises[pos], pos)
            }
        }
    }

    private fun buildRepsLine(exercise: Exercise): String {
        exercise.details.takeIf { it.isNotBlank() }?.let { return it }

        val custom = exercise.repsOrDuration?.takeIf { it.isNotBlank() }
        if (custom != null) {
            return buildString {
                if (exercise.sets > 0) append("${exercise.sets}x")
                append(custom)
                if (!exercise.equipmentLabel.isNullOrBlank()) {
                    append(" · ")
                    append(exercise.equipmentLabel)
                }
            }
        }
        return exercise.details.takeIf { it.isNotBlank() }
            ?: buildString {
                if (exercise.sets > 0 && exercise.repsHigh > 0) {
                    append("${exercise.sets}x${exercise.repsLow}-${exercise.repsHigh}")
                }
                if (!exercise.equipmentLabel.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(exercise.equipmentLabel)
                }
            }
    }
}

package com.tfm.galifit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tfm.galifit.R
import com.tfm.galifit.data.model.DayExercisePlan
import com.tfm.galifit.data.model.Exercise
import com.tfm.galifit.util.GalifitFlowLog

class DayExercisePlanAdapter(
    private val dias: MutableList<DayExercisePlan> = mutableListOf(),
    private val onAddExercise: ((dayIndex: Int) -> Unit)? = null,
    private val onEditExercise: ((dayIndex: Int, exercise: Exercise, exerciseIndex: Int) -> Unit)? = null,
    private val onDeleteExercise: ((dayIndex: Int, exercise: Exercise, exerciseIndex: Int) -> Unit)? = null
) : RecyclerView.Adapter<DayExercisePlanAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtDia: TextView = view.findViewById(R.id.txtDia)
        val recyclerExercises: RecyclerView = view.findViewById(R.id.recyclerExercises)
        val btnAdd: MaterialButton = view.findViewById(R.id.btnAddExercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.exercise_plan_day, parent, false)

        return DayViewHolder(view)
    }

    override fun getItemCount() = dias.size

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {

        val day = dias[position]

        holder.txtDia.text = when {
            day.isRest -> "${day.dayName} · Descanso"
            day.focus.isNotBlank() -> "${day.dayName} · ${day.focus}"
            else -> day.dayName
        }

        if (day.isRest) {
            holder.recyclerExercises.adapter = null
            holder.recyclerExercises.visibility = View.GONE
            holder.btnAdd.visibility = View.GONE
        } else {
            holder.recyclerExercises.visibility = View.VISIBLE
            holder.btnAdd.visibility = View.VISIBLE
            val exercisesAdapter = ExercisePlanAdapter(
                exercises = day.exercises,
                onEditExercise = { exercise, exerciseIndex ->
                    val dayPos = holder.bindingAdapterPosition
                    if (dayPos != RecyclerView.NO_POSITION) {
                        onEditExercise?.invoke(dayPos, exercise, exerciseIndex)
                    }
                },
                onDeleteExercise = { exercise, exerciseIndex ->
                    val dayPos = holder.bindingAdapterPosition
                    if (dayPos != RecyclerView.NO_POSITION) {
                        onDeleteExercise?.invoke(dayPos, exercise, exerciseIndex)
                    }
                }
            )
            holder.recyclerExercises.layoutManager =
                LinearLayoutManager(holder.itemView.context)
            holder.recyclerExercises.isNestedScrollingEnabled = false
            holder.recyclerExercises.adapter = exercisesAdapter
        }

        holder.btnAdd.setOnClickListener {
            val dayPos = holder.bindingAdapterPosition
            if (dayPos != RecyclerView.NO_POSITION) {
                onAddExercise?.invoke(dayPos)
            }
        }
    }

    fun replaceDays(newDays: List<DayExercisePlan>) {
        dias.clear()
        dias.addAll(newDays)
        val exercisesCount = newDays.sumOf { it.exercises.size }
        GalifitFlowLog.ui("Lista plan actualizada: ${newDays.size} días, $exercisesCount ejercicios")
        notifyDataSetChanged()
    }

    fun getDays(): List<DayExercisePlan> = dias

    fun addExerciseToDay(dayIndex: Int, exercise: Exercise) {
        if (dayIndex !in dias.indices) return
        dias[dayIndex].exercises.add(exercise)
        notifyItemChanged(dayIndex)
    }

    fun updateExerciseInDay(dayIndex: Int, exerciseIndex: Int, exercise: Exercise) {
        if (dayIndex !in dias.indices) return
        val list = dias[dayIndex].exercises
        if (exerciseIndex !in list.indices) return
        list[exerciseIndex] = exercise
        notifyItemChanged(dayIndex)
    }

    fun removeExerciseFromDay(dayIndex: Int, exerciseIndex: Int) {
        if (dayIndex !in dias.indices) return
        val list = dias[dayIndex].exercises
        if (exerciseIndex !in list.indices) return
        list.removeAt(exerciseIndex)
        notifyItemChanged(dayIndex)
    }
}

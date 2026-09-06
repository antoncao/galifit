package com.tfm.galifit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.tfm.galifit.R
import com.tfm.galifit.data.model.DayMealPlan
import com.tfm.galifit.data.model.Meal
import com.tfm.galifit.util.GalifitFlowLog

class DayMealPlanAdapter(
    private val dias: MutableList<DayMealPlan> = mutableListOf(),
    private val onAddPlato: ((dayIndex: Int) -> Unit)? = null,
    private val onEditCustomDish: ((dayIndex: Int, meal: Meal, mealPosition: Int) -> Unit)? = null,
    private val onDeleteMeal: ((dayIndex: Int, meal: Meal, mealPosition: Int) -> Unit)? = null
) : RecyclerView.Adapter<DayMealPlanAdapter.DayViewHolder>() {

    class DayViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtDia: TextView = view.findViewById(R.id.txtDia)
        val recyclerComidas: RecyclerView = view.findViewById(R.id.recyclerComidas)
        val btnAdd: MaterialButton = view.findViewById(R.id.btnAddPlato)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.meal_plan_day, parent, false)

        return DayViewHolder(view)
    }

    override fun getItemCount() = dias.size

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {

        val day = dias[position]

        holder.txtDia.text = day.dayName

        val comidasAdapter = MealPlanAdapter(
            meals = day.meals,
            onEditCustomDish = { meal, mealPos ->
                val dayPos = holder.bindingAdapterPosition
                if (dayPos != RecyclerView.NO_POSITION) {
                    onEditCustomDish?.invoke(dayPos, meal, mealPos)
                }
            },
            onDeleteMeal = { meal, mealPos ->
                val dayPos = holder.bindingAdapterPosition
                if (dayPos != RecyclerView.NO_POSITION) {
                    onDeleteMeal?.invoke(dayPos, meal, mealPos)
                }
            }
        )

        holder.recyclerComidas.layoutManager =
            LinearLayoutManager(holder.itemView.context)
        holder.recyclerComidas.isNestedScrollingEnabled = false

        holder.recyclerComidas.adapter = comidasAdapter

        holder.btnAdd.setOnClickListener {
            val dayPos = holder.bindingAdapterPosition
            if (dayPos != RecyclerView.NO_POSITION) {
                onAddPlato?.invoke(dayPos)
            }
        }
    }

    fun replaceDays(newDays: List<DayMealPlan>) {
        dias.clear()
        dias.addAll(newDays)
        val mealCount = newDays.sumOf { it.meals.size }
        GalifitFlowLog.ui("Lista plan actualizada: ${newDays.size} días, $mealCount comidas")
        notifyDataSetChanged()
    }

    fun getDays(): List<DayMealPlan> = dias

    fun addMealToDay(dayIndex: Int, meal: Meal) {
        if (dayIndex !in dias.indices) return
        dias[dayIndex].meals.add(meal)
        notifyItemChanged(dayIndex)
    }

    fun updateMealInDay(dayIndex: Int, mealIndex: Int, meal: Meal) {
        if (dayIndex !in dias.indices) return
        val meals = dias[dayIndex].meals
        if (mealIndex !in meals.indices) return
        meals[mealIndex] = meal
        notifyItemChanged(dayIndex)
    }

    fun hasCustomMeals(): Boolean = dias.any { day -> day.meals.any { it.isCustom } }
}

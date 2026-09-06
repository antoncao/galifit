package com.tfm.galifit.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tfm.galifit.MealDetailActivity
import com.tfm.galifit.R
import com.tfm.galifit.data.model.Meal
import com.tfm.galifit.data.model.bindMacroBadges
import com.tfm.galifit.data.model.isEditableCustomDish
import com.tfm.galifit.util.MealImageResolver

class MealPlanAdapter(
    private val meals: MutableList<Meal>,
    private val onEditCustomDish: ((meal: Meal, position: Int) -> Unit)? = null,
    private val onDeleteMeal: ((meal: Meal, position: Int) -> Unit)? = null
) : RecyclerView.Adapter<MealPlanAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtComida: TextView = view.findViewById(R.id.tvMealName)
        val tvCalories: TextView = view.findViewById(R.id.tvMealCalories)
        val tvProtein: TextView = view.findViewById(R.id.tvMealProtein)
        val tvCarbs: TextView = view.findViewById(R.id.tvMealCarbs)
        val tvFat: TextView = view.findViewById(R.id.tvMealFat)
        val imgComida: ImageView = view.findViewById(R.id.imgComida)
        val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
        val btnEditar: ImageButton = view.findViewById(R.id.btnEditar)

        val mealItem: View = view.findViewById(R.id.layoutMeal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.meal_plan_item_meal, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount() = meals.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val meal = meals[position]

        holder.txtComida.text = meal.name
        holder.tvCalories.text = "${meal.calories ?: 0} kcal"
        meal.bindMacroBadges(holder.tvProtein, holder.tvCarbs, holder.tvFat)
        holder.imgComida.contentDescription = meal.name

        val loadModel = MealImageResolver.resolveThumbnailLoadModel(holder.itemView.context, meal)

        if (loadModel != null) {
            Glide.with(holder.itemView.context)
                .load(loadModel)
                .centerCrop()
                .placeholder(R.drawable.bg_image_placeholder)
                .error(R.drawable.bg_image_placeholder)
                .into(holder.imgComida)
        } else {
            Glide.with(holder.itemView.context).clear(holder.imgComida)
            holder.imgComida.setImageDrawable(null)
            holder.imgComida.setBackgroundResource(R.drawable.bg_image_placeholder)
        }

        val isEditable = meal.isEditableCustomDish()

        if (isEditable) {
            holder.btnEditar.visibility = View.VISIBLE
            holder.btnEditar.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEditCustomDish?.invoke(meals[pos], pos)
                }
            }
        } else {
            holder.btnEditar.visibility = View.GONE
            holder.btnEditar.setOnClickListener(null)
        }

        holder.mealItem.setOnClickListener {
            if (isEditable) {
                onEditCustomDish?.invoke(meal, holder.bindingAdapterPosition)
                return@setOnClickListener
            }
            val context = holder.itemView.context
            context.startActivity(MealDetailActivity.intentFor(context, meal))
        }

        holder.btnEliminar.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val removed = meals[pos]
                meals.removeAt(pos)
                notifyItemRemoved(pos)
                onDeleteMeal?.invoke(removed, pos)
            }
        }
    }
}

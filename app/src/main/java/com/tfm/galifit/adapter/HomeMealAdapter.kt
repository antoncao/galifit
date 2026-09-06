package com.tfm.galifit.adapter

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.CheckBox

import android.widget.ImageView

import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide

import com.tfm.galifit.MealDetailActivity
import com.tfm.galifit.R

import com.tfm.galifit.data.model.Meal
import com.tfm.galifit.data.model.bindMacroBadges
import com.tfm.galifit.util.MealImageResolver

class HomeMealAdapter(

    private val meals: List<Meal>,
    initialCheckedNames: Set<String> = emptySet(),
    private val onCheckedChanged: (checkedNames: Set<String>, totalCheckedCalories: Int) -> Unit
) : RecyclerView.Adapter<HomeMealAdapter.VH>() {

    private val checkedStates = BooleanArray(meals.size) { i ->

        meals[i].name in initialCheckedNames
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {

        val view = LayoutInflater.from(parent.context)

            .inflate(R.layout.item_home_meal, parent, false)

        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {

        val meal = meals[position]

        holder.tvName.text = meal.name

        holder.tvCalories.text = "${meal.calories ?: 0} kcal"

        meal.bindMacroBadges(holder.tvProtein, holder.tvCarbs, holder.tvFat)

        val loadModel = MealImageResolver.resolveThumbnailLoadModel(holder.itemView.context, meal)

        if (loadModel != null) {
            Glide.with(holder.itemView.context)

                .load(loadModel)

                .centerCrop()

                .placeholder(R.drawable.bg_image_placeholder)

                .error(R.drawable.bg_image_placeholder)

                .into(holder.imgMeal)

        } else {
            Glide.with(holder.itemView.context).clear(holder.imgMeal)

            holder.imgMeal.setImageDrawable(null)

            holder.imgMeal.setBackgroundResource(R.drawable.bg_image_placeholder)
        }

        holder.cb.setOnCheckedChangeListener(null)

        holder.cb.isChecked = checkedStates[position]

        holder.cb.setOnCheckedChangeListener { _, isChecked ->

            checkedStates[position] = isChecked

            onCheckedChanged(getCheckedNames(), computeCheckedCalories())
        }

        holder.layoutContent.setOnClickListener {
            val context = holder.itemView.context
            context.startActivity(MealDetailActivity.intentFor(context, meal))
        }
    }

    override fun getItemCount(): Int = meals.size

    fun computeCheckedCalories(): Int {

        var total = 0

        for (i in meals.indices) {
            if (checkedStates[i]) total += meals[i].calories ?: 0
        }

        return total
    }

    fun getCheckedNames(): Set<String> {

        val names = mutableSetOf<String>()

        for (i in meals.indices) {
            if (checkedStates[i]) names.add(meals[i].name)
        }

        return names
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {

        val cb: CheckBox = view.findViewById(R.id.cbMealDone)

        val layoutContent: View = view.findViewById(R.id.layoutHomeMealContent)

        val imgMeal: ImageView = view.findViewById(R.id.imgMeal)

        val tvName: TextView = view.findViewById(R.id.tvHomeMealName)

        val tvCalories: TextView = view.findViewById(R.id.tvHomeMealCalories)

        val tvProtein: TextView = view.findViewById(R.id.tvHomeMealProtein)

        val tvCarbs: TextView = view.findViewById(R.id.tvHomeMealCarbs)

        val tvFat: TextView = view.findViewById(R.id.tvHomeMealFat)
    }
}

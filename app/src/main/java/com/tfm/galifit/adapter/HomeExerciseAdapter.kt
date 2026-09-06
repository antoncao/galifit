package com.tfm.galifit.adapter

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.CheckBox

import android.widget.ImageView

import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.bumptech.glide.Glide

import com.tfm.galifit.R

import com.tfm.galifit.data.model.Exercise

class HomeExerciseAdapter(

    private val exercises: List<Exercise>,
    initialCheckedNames: Set<String> = emptySet(),
    private val onCheckedChanged: (checkedNames: Set<String>) -> Unit = {}
) : RecyclerView.Adapter<HomeExerciseAdapter.VH>() {

    private val checkedStates = BooleanArray(exercises.size) { i ->

        exercises[i].name in initialCheckedNames
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {

        val view = LayoutInflater.from(parent.context)

            .inflate(R.layout.item_home_exercise, parent, false)

        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {

        val exercise = exercises[position]

        holder.tvName.text = exercise.name

        holder.tvDetails.text = exercise.details

        val img = exercise.thumbnailImageUrl

        if (!img.isNullOrBlank()) {
            holder.imgExercise.setBackgroundResource(R.drawable.bg_image_placeholder)

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

        holder.cb.setOnCheckedChangeListener(null)

        holder.cb.isChecked = checkedStates[position]

        holder.cb.setOnCheckedChangeListener { _, isChecked ->

            checkedStates[position] = isChecked

            onCheckedChanged(getCheckedNames())
        }
    }

    override fun getItemCount(): Int = exercises.size

    fun getCheckedNames(): Set<String> {

        val names = mutableSetOf<String>()

        for (i in exercises.indices) {
            if (checkedStates[i]) names.add(exercises[i].name)
        }

        return names
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {

        val cb: CheckBox = view.findViewById(R.id.cbExerciseDone)

        val tvName: TextView = view.findViewById(R.id.tvHomeExerciseName)

        val tvDetails: TextView = view.findViewById(R.id.tvHomeExerciseDetails)

        val imgExercise: ImageView = view.findViewById(R.id.imgExercise)
    }
}

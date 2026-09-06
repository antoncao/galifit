package com.tfm.galifit.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tfm.galifit.R

data class ShoppingItem(
    val foodId: String,
    val name: String,
    val quantityText: String,
    var checked: Boolean = false
)

class ShoppingListAdapter(
    private val items: MutableList<ShoppingItem>,
    private val onCheckedChanged: (checkedFoodIds: Set<String>) -> Unit
) : RecyclerView.Adapter<ShoppingListAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shopping_ingredient, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvQty.text = item.quantityText

        applyStrikethrough(holder.tvName, item.checked)

        holder.cb.setOnCheckedChangeListener(null)
        holder.cb.isChecked = item.checked
        holder.cb.setOnCheckedChangeListener { _, isChecked ->
            items[position].checked = isChecked
            applyStrikethrough(holder.tvName, isChecked)
            onCheckedChanged(getCheckedIds())
        }
    }

    override fun getItemCount(): Int = items.size

    fun getCheckedIds(): Set<String> =
        items.filter { it.checked }.map { it.foodId }.toSet()

    fun selectAll() {
        items.forEach { it.checked = true }
        notifyDataSetChanged()
        onCheckedChanged(getCheckedIds())
    }

    fun deselectAll() {
        items.forEach { it.checked = false }
        notifyDataSetChanged()
        onCheckedChanged(getCheckedIds())
    }

    fun isAllChecked(): Boolean = items.isNotEmpty() && items.all { it.checked }

    private fun applyStrikethrough(tv: TextView, checked: Boolean) {
        if (checked) {
            tv.paintFlags = tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            tv.paintFlags = tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cb: CheckBox = view.findViewById(R.id.cbIngredient)
        val tvName: TextView = view.findViewById(R.id.tvIngredientName)
        val tvQty: TextView = view.findViewById(R.id.tvIngredientQty)
    }
}

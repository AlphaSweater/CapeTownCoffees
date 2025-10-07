//======================================================================================
//Group 2 - Group Members:
//======================================================================================
//* Chad Fairlie ST10269509
//* Dhiren Ruthenavelu ST10256859
//* Kayla Ferreira ST10259527
//* Nathan Teixeira ST10249266
//======================================================================================
//References:
//======================================================================================
//* ChatGPT was used to guide the structure of this Adapter, including the ViewHolder
//setup, data binding logic, and handling click listeners.
//* Assistance was also provided for optimizing RecyclerView performance and readability.
//* It also helped generate useful comments
//======================================================================================

package com.synaptix.capetowncoffees.ui.lists.favouriteList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R


class FavouriteAdapter(
    private val items: List<FavouriteItem>,
    private val onItemClick: (FavouriteItem) -> Unit = {}
) : RecyclerView.Adapter<FavouriteAdapter.FavVH>() {

    class FavVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.ivCafe)
        val name: TextView = itemView.findViewById(R.id.tvName)
        val location: TextView = itemView.findViewById(R.id.tvLocation)
        val rating: TextView = itemView.findViewById(R.id.tvRating)
        val btnDownload: View = itemView.findViewById(R.id.btnDownload)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_favourite, parent, false)
        return FavVH(v)
    }

    override fun onBindViewHolder(holder: FavVH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.location.text = item.location
        holder.rating.text = item.ratingText
        // Image is a placeholder; no loading library used for now
        holder.btnDownload.setOnClickListener { /* no-op for UI only */ }
        holder.itemView.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size
}

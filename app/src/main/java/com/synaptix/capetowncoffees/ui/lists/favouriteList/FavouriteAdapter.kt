package com.synaptix.capetowncoffees.ui.lists.favouriteList

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R

// ─────────── Adapter ───────────
// Renders a simple static list of favourite items; click forwards the tapped model.
public class FavouriteAdapter(
    private val items: List<FavouriteItem>,
    private val onItemClick: (FavouriteItem) -> Unit = {}
) : RecyclerView.Adapter<FavouriteAdapter.FavVH>() {

    // ─────────── ViewHolder ───────────
    // Holds view refs so binds stay cheap; no image loading here (placeholder only).
    public class FavVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        public val image: ImageView = itemView.findViewById(R.id.ivCafe)
        public val name: TextView = itemView.findViewById(R.id.tvName)
        public val location: TextView = itemView.findViewById(R.id.tvLocation)
        public val rating: TextView = itemView.findViewById(R.id.tvRating)
        public val btnDownload: View = itemView.findViewById(R.id.btnDownload)
    }

    // ─────────── Adapter Overrides ───────────
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favourite, parent, false)
        return FavVH(view)
    }

    override fun onBindViewHolder(holder: FavVH, position: Int) {
        val item = items[position]
        with(holder) {
            name.text = item.name
            location.text = item.location
            rating.text = item.ratingText
            // keep placeholder; click is delegated to the row
            btnDownload.setOnClickListener { /* no-op: UI-only button */ }
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    override fun getItemCount(): Int = items.size
}
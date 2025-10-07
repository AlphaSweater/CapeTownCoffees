package com.synaptix.capetowncoffees.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R

public class ReviewAdapter(
    private val items: List<ReviewItem>
) : RecyclerView.Adapter<ReviewAdapter.ReviewVH>() {

    // ─────────── ViewHolder ───────────
    // Holds references to row views so binds are cheap.
    public class ReviewVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCafeName: TextView = itemView.findViewById(R.id.tvCafeName)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val ratingBar: AppCompatRatingBar = itemView.findViewById(R.id.ratingBar)
        val tvReviewText: TextView = itemView.findViewById(R.id.tvReviewText)
        val tvLikes: TextView = itemView.findViewById(R.id.tvLikes)
    }

    // ─────────── Adapter Lifecycle ───────────
    // Inflates the row layout; no heavy work here.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_review, parent, false)
        return ReviewVH(view)
    }

    // Binds one review to views; pure data → UI mapping.
    override fun onBindViewHolder(holder: ReviewVH, position: Int) {
        val item = items[position]
        holder.tvCafeName.text = item.cafeName
        holder.tvDate.text = item.dateText
        holder.ratingBar.rating = item.rating
        holder.tvReviewText.text = item.reviewText
        holder.tvLikes.text = item.likes.toString()
    }

    // Keeps RecyclerView sizing stable.
    override fun getItemCount(): Int = items.size
}

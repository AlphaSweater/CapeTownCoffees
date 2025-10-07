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

package com.synaptix.capetowncoffees.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRatingBar
import androidx.recyclerview.widget.RecyclerView
import com.synaptix.capetowncoffees.R

class ReviewAdapter(
    private val items: List<ReviewItem>
) : RecyclerView.Adapter<ReviewAdapter.ReviewVH>() {

    class ReviewVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCafeName: TextView = itemView.findViewById(R.id.tvCafeName)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val ratingBar: AppCompatRatingBar = itemView.findViewById(R.id.ratingBar)
        val tvReviewText: TextView = itemView.findViewById(R.id.tvReviewText)
        val tvLikes: TextView = itemView.findViewById(R.id.tvLikes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return ReviewVH(v)
    }

    override fun onBindViewHolder(holder: ReviewVH, position: Int) {
        val item = items[position]
        holder.tvCafeName.text = item.cafeName
        holder.tvDate.text = item.dateText
        holder.ratingBar.rating = item.rating
        holder.tvReviewText.text = item.reviewText
        holder.tvLikes.text = item.likes.toString()
    }

    override fun getItemCount(): Int = items.size
}

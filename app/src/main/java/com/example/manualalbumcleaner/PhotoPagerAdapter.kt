package com.example.manualalbumcleaner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class PhotoPagerAdapter(
    private val photos: List<PhotoItem>
) : RecyclerView.Adapter<PhotoPagerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgPhoto: ImageView = view.findViewById(R.id.imgPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_page, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        Glide.with(holder.imgPhoto)
            .load(photo.uri)
            .placeholder(android.R.color.transparent)
            .fitCenter()
            .into(holder.imgPhoto)
    }

    override fun getItemCount() = photos.size
}

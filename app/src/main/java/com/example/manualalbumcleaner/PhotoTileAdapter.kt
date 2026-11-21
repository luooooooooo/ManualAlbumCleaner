package com.example.manualalbumcleaner

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView

/** 选择起始照片页的缩略图网格 */
class PhotoTileAdapter(
    private val photos: List<PhotoItem>,
    private val onClick: (position: Int) -> Unit
) : RecyclerView.Adapter<PhotoTileAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val imgPhoto: ShapeableImageView = view.findViewById(R.id.imgPhoto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_tile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        Glide.with(holder.itemView.context)
            .load(photo.uri)
            .placeholder(R.drawable.bg_photo_placeholder)
            .centerCrop()
            .into(holder.imgPhoto)

        holder.itemView.setOnClickListener {
            val index = holder.bindingAdapterPosition
            if (index != RecyclerView.NO_POSITION) onClick(index)
        }
    }

    override fun getItemCount() = photos.size
}

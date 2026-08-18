package com.example.manualalbumcleaner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SelectablePhotoAdapter(
    private var photos: List<PhotoItem>,
    private val isSelectionMode: Boolean = false,
    private val selectedIds: MutableSet<Long> = mutableSetOf(),
    private val onSelectionChange: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<SelectablePhotoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgPhoto: ImageView = view.findViewById(R.id.imgPhoto)
        val selectionOverlay: View = view.findViewById(R.id.selectionOverlay)
        val checkbox: ImageView = view.findViewById(R.id.checkbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo_selectable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        
        Glide.with(holder.itemView.context)
            .load(photo.uri)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(holder.imgPhoto)

        if (isSelectionMode) {
            holder.checkbox.visibility = View.VISIBLE
            holder.selectionOverlay.visibility = View.VISIBLE
            
            if (selectedIds.contains(photo.id)) {
                holder.checkbox.setImageResource(R.drawable.ic_checkbox_checked)
                holder.selectionOverlay.alpha = 0.3f
            } else {
                holder.checkbox.setImageResource(R.drawable.ic_checkbox_unchecked)
                holder.selectionOverlay.alpha = 0f
            }
        } else {
            holder.checkbox.visibility = View.GONE
            holder.selectionOverlay.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                if (selectedIds.contains(photo.id)) {
                    selectedIds.remove(photo.id)
                } else {
                    selectedIds.add(photo.id)
                }
                notifyItemChanged(position)
                onSelectionChange?.invoke(selectedIds.size)
            }
        }
    }

    override fun getItemCount() = photos.size

    fun updatePhotos(newPhotos: List<PhotoItem>) {
        photos = newPhotos
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun getSelectedPhotos(): List<PhotoItem> {
        return photos.filter { selectedIds.contains(it.id) }
    }

    fun getSelectedIds(): Set<Long> {
        return selectedIds.toSet()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }
}
package com.example.manualalbumcleaner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class SelectablePhotoAdapter(
    private var photos: List<PhotoItem>,
    private var isSelectionMode: Boolean = false,
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

        Glide.with(holder.imgPhoto)
            .load(photo.uri)
            .placeholder(R.drawable.bg_photo_placeholder)
            .centerCrop()
            .into(holder.imgPhoto)

        val selected = selectedIds.contains(photo.id)
        holder.checkbox.isVisible = isSelectionMode
        holder.selectionOverlay.isVisible = isSelectionMode && selected
        holder.checkbox.setImageResource(
            if (selected) R.drawable.ic_check_circle else R.drawable.ic_check_circle_outline
        )

        holder.itemView.setOnClickListener {
            if (!isSelectionMode) return@setOnClickListener
            if (selectedIds.contains(photo.id)) {
                selectedIds.remove(photo.id)
            } else {
                selectedIds.add(photo.id)
            }
            notifyItemChanged(position)
            onSelectionChange?.invoke(selectedIds.size)
        }
    }

    override fun getItemCount() = photos.size

    fun setPhotos(newPhotos: List<PhotoItem>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
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

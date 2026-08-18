package com.example.manualalbumcleaner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView

class AlbumAdapter(
    private val albums: List<MainActivity.AlbumInfo>,
    private val isGridView: Boolean,
    private val onClick: (MainActivity.AlbumInfo) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ShapeableImageView? = if (view.findViewById<ShapeableImageView>(R.id.imgCover) != null) {
            view.findViewById(R.id.imgCover)
        } else null
        val imgFolderIcon: ImageView? = if (view.findViewById<ImageView>(R.id.imgFolderIcon) != null) {
            view.findViewById(R.id.imgFolderIcon)
        } else null
        val tvAlbumName: TextView = view.findViewById(R.id.tvAlbumName)
        val tvPhotoCount: TextView = view.findViewById(R.id.tvPhotoCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isGridView) R.layout.item_album_grid else R.layout.item_album_list
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albums[position]
        holder.tvAlbumName.text = album.bucketName
        holder.tvPhotoCount.text = holder.itemView.context.getString(R.string.photo_count, album.count)

        if (isGridView && holder.imgCover != null) {
            Glide.with(holder.itemView.context)
                .load(album.coverUri)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(holder.imgCover)
        }

        holder.itemView.setOnClickListener { onClick(album) }
    }

    override fun getItemCount() = albums.size
}

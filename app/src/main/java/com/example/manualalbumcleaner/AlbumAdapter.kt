package com.example.manualalbumcleaner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView

class AlbumAdapter(
    private var albums: List<MainActivity.AlbumInfo>,
    private var isGridView: Boolean,
    private val onClick: (MainActivity.AlbumInfo) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ShapeableImageView = view.findViewById(R.id.imgCover)
        val tvAlbumName: TextView = view.findViewById(R.id.tvAlbumName)
        val tvPhotoCount: TextView = view.findViewById(R.id.tvPhotoCount)
    }

    fun setData(newAlbums: List<MainActivity.AlbumInfo>, grid: Boolean) {
        albums = newAlbums
        isGridView = grid
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isGridView) R.layout.item_album_grid else R.layout.item_album_list
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = albums[position]
        holder.tvAlbumName.text = album.bucketName
        holder.tvPhotoCount.text =
            holder.itemView.context.getString(R.string.photo_count, album.count)

        Glide.with(holder.itemView.context)
            .load(album.coverUri)
            .placeholder(R.drawable.bg_photo_placeholder)
            .centerCrop()
            .into(holder.imgCover)

        holder.itemView.setOnClickListener { onClick(album) }
    }

    override fun getItemCount() = albums.size
}

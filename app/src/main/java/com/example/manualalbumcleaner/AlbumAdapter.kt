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
    private var columns: Int,
    private val onClick: (MainActivity.AlbumInfo) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ShapeableImageView = view.findViewById(R.id.imgCover)
        val tvAlbumName: TextView = view.findViewById(R.id.tvAlbumName)
        val tvPhotoCount: TextView = view.findViewById(R.id.tvPhotoCount)
    }

    /** 4 列及以上改用紧凑条目，文字与留白一起缩小 */
    private val isCompact: Boolean
        get() = isGridView && columns >= 4

    fun setData(newAlbums: List<MainActivity.AlbumInfo>, grid: Boolean) {
        albums = newAlbums
        isGridView = grid
        notifyDataSetChanged()
    }

    fun setColumns(count: Int) {
        if (columns == count) return
        columns = count
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when {
        !isGridView -> TYPE_LIST
        isCompact -> TYPE_GRID_COMPACT
        else -> TYPE_GRID
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = when (viewType) {
            TYPE_LIST -> R.layout.item_album_list
            TYPE_GRID_COMPACT -> R.layout.item_album_grid_compact
            else -> R.layout.item_album_grid
        }
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

    private companion object {
        const val TYPE_GRID = 0
        const val TYPE_GRID_COMPACT = 1
        const val TYPE_LIST = 2
    }
}

package com.example.manualalbumcleaner

import android.content.ContentResolver
import android.provider.MediaStore

/**
 * 相册照片查询：浏览页和选择起始照片页共用同一套逻辑，保证两边的顺序与内容一致。
 * 已在回收站里的照片通过传入的 id 集合一次性排除（集合由 TrashManager 缓存），
 * 不再对每张照片单独解析回收站 JSON。
 */
object AlbumPhotoLoader {

    fun load(resolver: ContentResolver, bucketId: String, trashIds: Set<Long>): List<PhotoItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            volumeColumnName()
        ).filterNotNull().toTypedArray()

        val photos = ArrayList<PhotoItem>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.BUCKET_ID} = ?",
            arrayOf(bucketId),
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val volumeColumn = volumeColumnName()?.let { cursor.getColumnIndex(it) } ?: -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                if (id in trashIds) continue
                val name = cursor.getString(nameColumn) ?: ""
                val bId = cursor.getString(bucketIdColumn) ?: ""
                val bName = cursor.getString(bucketNameColumn) ?: ""
                val volume = if (volumeColumn >= 0) cursor.getString(volumeColumn) else null
                photos.add(PhotoItem(id, imageUriOf(id, volume), name, bId, bName))
            }
        }
        return photos
    }
}

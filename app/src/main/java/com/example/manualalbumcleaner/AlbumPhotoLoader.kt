package com.example.manualalbumcleaner

import android.content.ContentResolver
import android.provider.MediaStore

/**
 * 相册照片查询：浏览页和选择起始照片页共用同一套逻辑，保证两边的顺序与内容一致。
 * 已在回收站里的照片通过传入的 id 集合一次性排除（集合由 TrashManager 缓存），
 * 不再对每张照片单独解析回收站 JSON。
 */
object AlbumPhotoLoader {

    fun load(
        resolver: ContentResolver,
        bucketId: String,
        trashIds: Set<Long>,
        filter: PhotoFilter = PhotoFilter.ALL,
        sort: SortOrder = SortOrder.NEWEST_FIRST
    ): List<PhotoItem> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.MIME_TYPE,
            volumeColumnName()
        ).filterNotNull().toTypedArray()

        val order = if (sort == SortOrder.NEWEST_FIRST) {
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
        } else {
            "${MediaStore.Images.Media.DATE_ADDED} ASC, ${MediaStore.Images.Media._ID} ASC"
        }

        val photos = ArrayList<PhotoItem>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Images.Media.BUCKET_ID} = ?",
            arrayOf(bucketId),
            order
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val mimeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
            val volumeColumn = volumeColumnName()?.let { cursor.getColumnIndex(it) } ?: -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                if (id in trashIds) continue

                val name = cursor.getString(nameColumn) ?: ""
                val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn) ?: "" else ""
                // DATE_ADDED 单位是秒，DATE_TAKEN 单位是毫秒
                val dateTaken = if (dateTakenColumn >= 0) cursor.getLong(dateTakenColumn) else 0L
                val dateAdded = cursor.getLong(dateAddedColumn) * 1000L
                val dateMillis = if (dateTaken > 0) dateTaken else dateAdded

                val photo = PhotoItem(
                    id = id,
                    uri = imageUriOf(id, if (volumeColumn >= 0) cursor.getString(volumeColumn) else null),
                    name = name,
                    bucketId = cursor.getString(bucketIdColumn) ?: "",
                    bucketName = cursor.getString(bucketNameColumn) ?: "",
                    dateMillis = dateMillis,
                    mimeType = mime
                )

                if (!PhotoFormat.matches(photo, filter)) continue
                photos.add(photo)
            }
        }
        return photos
    }
}

package com.example.manualalbumcleaner

import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * MediaStore 的 _ID 在内部存储与 SD 卡等不同 volume 之间是各自自增的，可能重复。
 * 因此拼 Uri 时必须带上照片真实所属的 volume，不能统一用 EXTERNAL_CONTENT_URI，
 * 否则在双存储设备上可能指向同 id 的另一张照片，或直接查不到记录。
 */
fun imageUriOf(id: Long, volume: String?): Uri {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val volumeName = if (volume.isNullOrEmpty()) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY
        } else {
            volume
        }
        return ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri(volumeName), id)
    }
    return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
}

/** volume 列自 Android 10 (API 29) 起才有 */
fun volumeColumnName(): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.MediaColumns.VOLUME_NAME
    } else {
        null
    }
}

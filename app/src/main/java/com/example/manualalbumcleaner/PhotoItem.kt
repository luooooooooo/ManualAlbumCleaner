package com.example.manualalbumcleaner

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucketId: String,
    val bucketName: String,
    /** 用于排序和日期定位的时间（毫秒）；优先拍摄时间，没有则用添加时间 */
    val dateMillis: Long = 0L,
    val mimeType: String = ""
)

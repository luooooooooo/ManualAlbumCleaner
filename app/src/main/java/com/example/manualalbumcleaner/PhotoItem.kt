package com.example.manualalbumcleaner

import android.net.Uri

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val bucketId: String,
    val bucketName: String
)

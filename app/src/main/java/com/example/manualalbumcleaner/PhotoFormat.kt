package com.example.manualalbumcleaner

/** 浏览队列的类型筛选 */
enum class PhotoFilter { ALL, JPG, RAW }

/** 浏览队列的时间顺序 */
enum class SortOrder { NEWEST_FIRST, OLDEST_FIRST }

/**
 * RAW / JPG 的判定。MediaStore 的 MIME 对很多 RAW 并不规范，
 * 所以先用文件名扩展名判定，再用 MIME 兜底。
 */
object PhotoFormat {

    private val RAW_EXTENSIONS = setOf(
        "dng", "arw", "cr2", "cr3", "nef", "nrw", "orf", "raf", "rw2", "raw",
        "pef", "srw", "x3f", "3fr", "erf", "mef", "mos", "iiq", "rwl",
        "srf", "kdc", "dcr", "sr2"
    )

    private val JPEG_EXTENSIONS = setOf("jpg", "jpeg", "jpe", "jfif")

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot in 0 until name.length - 1) name.substring(dot + 1).lowercase() else ""
    }

    fun isRaw(photo: PhotoItem): Boolean {
        if (extensionOf(photo.name) in RAW_EXTENSIONS) return true
        return photo.mimeType.lowercase().startsWith("image/x-")
    }

    fun isJpeg(photo: PhotoItem): Boolean {
        if (extensionOf(photo.name) in JPEG_EXTENSIONS) return true
        return photo.mimeType.equals("image/jpeg", ignoreCase = true)
    }

    fun matches(photo: PhotoItem, filter: PhotoFilter): Boolean = when (filter) {
        PhotoFilter.ALL -> true
        PhotoFilter.JPG -> isJpeg(photo)
        PhotoFilter.RAW -> isRaw(photo)
    }
}

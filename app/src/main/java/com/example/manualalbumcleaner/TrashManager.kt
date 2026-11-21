package com.example.manualalbumcleaner

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray

class TrashManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("trash_prefs", Context.MODE_PRIVATE)

    /** 回收站 id 的内存缓存：避免每张照片都重新解析一遍整份 JSON */
    @Volatile
    private var cachedIds: Set<Long>? = null

    fun addToTrash(photo: PhotoItem) {
        val list = getTrashList().toMutableList()
        if (list.none { it.id == photo.id }) {
            list.add(photo)
            saveList(list)
        }
    }

    fun removeFromTrash(photo: PhotoItem) {
        val list = getTrashList().toMutableList()
        list.removeAll { it.id == photo.id }
        saveList(list)
    }

    fun getTrashList(): List<PhotoItem> {
        val json = prefs.getString("trash_items", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<PhotoItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                PhotoItem(
                    id = obj.getLong("id"),
                    uri = Uri.parse(obj.getString("uri")),
                    name = obj.getString("name"),
                    bucketId = obj.getString("bucketId"),
                    bucketName = obj.getString("bucketName")
                )
            )
        }
        return list
    }

    fun clearTrash() {
        prefs.edit().remove("trash_items").apply()
    }

    fun isInTrash(photoId: Long): Boolean {
        return photoId in getTrashIds()
    }

    /** 一次性拿到回收站里的全部照片 id，批量过滤用 */
    fun getTrashIds(): Set<Long> {
        cachedIds?.let { return it }
        val ids = getTrashList().mapTo(HashSet()) { it.id }
        cachedIds = ids
        return ids
    }

    private fun saveList(list: List<PhotoItem>) {
        cachedIds = null
        val array = JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject()
            obj.put("id", item.id)
            obj.put("uri", item.uri.toString())
            obj.put("name", item.name)
            obj.put("bucketId", item.bucketId)
            obj.put("bucketName", item.bucketName)
            array.put(obj)
        }
        prefs.edit().putString("trash_items", array.toString()).apply()
    }
}

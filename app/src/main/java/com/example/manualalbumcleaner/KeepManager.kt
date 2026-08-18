package com.example.manualalbumcleaner

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray

class KeepManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("keep_prefs", Context.MODE_PRIVATE)

    fun addToKeep(photo: PhotoItem) {
        val list = getKeepList().toMutableList()
        if (list.none { it.id == photo.id }) {
            list.add(photo)
            saveList(list)
        }
    }

    fun removeFromKeep(photo: PhotoItem) {
        val list = getKeepList().toMutableList()
        list.removeAll { it.id == photo.id }
        saveList(list)
    }

    fun getKeepList(): List<PhotoItem> {
        val json = prefs.getString("keep_items", "[]") ?: "[]"
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

    fun clearKeep() {
        prefs.edit().remove("keep_items").apply()
    }

    fun isKept(photoId: Long): Boolean {
        return getKeepList().any { it.id == photoId }
    }

    private fun saveList(list: List<PhotoItem>) {
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
        prefs.edit().putString("keep_items", array.toString()).apply()
    }
}

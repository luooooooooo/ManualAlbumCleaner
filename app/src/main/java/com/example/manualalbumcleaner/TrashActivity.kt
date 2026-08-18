package com.example.manualalbumcleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class TrashActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnSelect: MaterialButton
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var tvSelectionInfo: TextView
    private lateinit var recyclerTrash: RecyclerView
    private lateinit var selectionActions: View
    private lateinit var btnRestoreSelected: MaterialButton
    private lateinit var btnDeleteSelected: MaterialButton
    private lateinit var trashManager: TrashManager
    private lateinit var adapter: SelectablePhotoAdapter
    
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<Long>()
    private var pendingDeletePhotos: List<PhotoItem>? = null

    companion object {
        private const val REQUEST_DELETE_PERMISSION = 2001
        private const val REQUEST_WRITE_PERMISSION = 2002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        toolbar = findViewById(R.id.toolbar)
        btnSelect = findViewById(R.id.btnSelect)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        tvSelectionInfo = findViewById(R.id.tvSelectionInfo)
        recyclerTrash = findViewById(R.id.recyclerTrash)
        selectionActions = findViewById(R.id.selectionActions)
        btnRestoreSelected = findViewById(R.id.btnRestoreSelected)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        trashManager = TrashManager(this)

        toolbar.setNavigationOnClickListener { finish() }

        recyclerTrash.layoutManager = GridLayoutManager(this, 3)
        adapter = SelectablePhotoAdapter(emptyList(), false, selectedIds) { count ->
            updateSelectionInfo(count)
        }
        recyclerTrash.adapter = adapter

        btnSelect.setOnClickListener { toggleSelectionMode() }
        btnSelectAll.setOnClickListener { selectAll() }
        btnRestoreSelected.setOnClickListener { restoreSelected() }
        btnDeleteSelected.setOnClickListener { deleteSelected() }

        loadTrash()
    }

    override fun onResume() {
        super.onResume()
        loadTrash()
    }

    private fun loadTrash() {
        val list = trashManager.getTrashList()
        adapter = SelectablePhotoAdapter(list, isSelectionMode, selectedIds) { count ->
            updateSelectionInfo(count)
        }
        recyclerTrash.adapter = adapter
        updateSelectionInfo(selectedIds.size)
    }

    private fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        
        if (isSelectionMode) {
            btnSelect.text = getString(R.string.select)
            btnSelectAll.visibility = View.VISIBLE
            tvSelectionInfo.visibility = View.VISIBLE
            selectionActions.visibility = View.VISIBLE
            selectedIds.clear()
        } else {
            btnSelect.text = getString(R.string.select)
            btnSelectAll.visibility = View.GONE
            tvSelectionInfo.visibility = View.GONE
            selectionActions.visibility = View.GONE
            selectedIds.clear()
        }
        
        loadTrash()
    }

    private fun selectAll() {
        val allPhotos = trashManager.getTrashList()
        selectedIds.clear()
        for (photo in allPhotos) {
            selectedIds.add(photo.id)
        }
        loadTrash()
    }

    private fun updateSelectionInfo(count: Int) {
        if (isSelectionMode) {
            tvSelectionInfo.text = if (count > 0) {
                getString(R.string.selected_count, count)
            } else {
                getString(R.string.tap_to_select)
            }
        }
    }

    private fun restoreSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show()
            return
        }

        val allPhotos = trashManager.getTrashList()
        for (photo in allPhotos) {
            if (selectedIds.contains(photo.id)) {
                trashManager.removeFromTrash(photo)
            }
        }

        Toast.makeText(this, "已恢复 ${selectedIds.size} 张照片", Toast.LENGTH_SHORT).show()
        selectedIds.clear()
        toggleSelectionMode()
        loadTrash()
    }

    private fun deleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show()
            return
        }

        val allPhotos = trashManager.getTrashList()
        val photosToDelete = allPhotos.filter { selectedIds.contains(it.id) }

        if (photosToDelete.isEmpty()) return
        pendingDeletePhotos = photosToDelete

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: 使用系统删除请求对话框
            val uris = photosToDelete.map { it.uri }
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                startIntentSenderForResult(
                    pendingIntent.intentSender,
                    REQUEST_DELETE_PERMISSION,
                    null, 0, 0, 0
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "删除请求失败", Toast.LENGTH_SHORT).show()
                pendingDeletePhotos = null
            }
        } else {
            // Android 10 及以下: 检查写入权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_WRITE_PERMISSION
                    )
                    return
                }
            }
            performDelete(photosToDelete)
        }
    }

    private fun performDelete(photos: List<PhotoItem>) {
        var deletedCount = 0
        for (photo in photos) {
            if (deletePhotoFile(photo.uri)) {
                trashManager.removeFromTrash(photo)
                deletedCount++
            }
        }

        Toast.makeText(this, "已彻底删除 $deletedCount 张照片", Toast.LENGTH_SHORT).show()
        selectedIds.clear()
        pendingDeletePhotos = null
        toggleSelectionMode()
        loadTrash()
    }

    private fun deletePhotoFile(uri: Uri): Boolean {
        return try {
            contentResolver.delete(uri, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DELETE_PERMISSION && resultCode == RESULT_OK) {
            pendingDeletePhotos?.let { photos ->
                // 用户已确认系统删除请求，从回收站移除记录
                for (photo in photos) {
                    trashManager.removeFromTrash(photo)
                }
                Toast.makeText(this, "已彻底删除 ${photos.size} 张照片", Toast.LENGTH_SHORT).show()
                selectedIds.clear()
                pendingDeletePhotos = null
                toggleSelectionMode()
                loadTrash()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingDeletePhotos?.let { performDelete(it) }
        } else if (requestCode == REQUEST_WRITE_PERMISSION) {
            Toast.makeText(this, "需要存储写入权限才能删除照片", Toast.LENGTH_SHORT).show()
            pendingDeletePhotos = null
        }
    }
}
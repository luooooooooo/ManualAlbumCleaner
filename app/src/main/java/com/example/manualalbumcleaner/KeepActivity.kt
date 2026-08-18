package com.example.manualalbumcleaner

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class KeepActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnSelect: MaterialButton
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var tvSelectionInfo: TextView
    private lateinit var recyclerKeep: RecyclerView
    private lateinit var selectionActions: View
    private lateinit var btnDeleteSelected: MaterialButton
    private lateinit var keepManager: KeepManager
    private lateinit var adapter: SelectablePhotoAdapter
    
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keep)

        toolbar = findViewById(R.id.toolbar)
        btnSelect = findViewById(R.id.btnSelect)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        tvSelectionInfo = findViewById(R.id.tvSelectionInfo)
        recyclerKeep = findViewById(R.id.recyclerKeep)
        selectionActions = findViewById(R.id.selectionActions)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        keepManager = KeepManager(this)

        toolbar.setNavigationOnClickListener { finish() }

        recyclerKeep.layoutManager = GridLayoutManager(this, 3)
        adapter = SelectablePhotoAdapter(emptyList(), false, selectedIds) { count ->
            updateSelectionInfo(count)
        }
        recyclerKeep.adapter = adapter

        btnSelect.setOnClickListener { toggleSelectionMode() }
        btnSelectAll.setOnClickListener { selectAll() }
        btnDeleteSelected.setOnClickListener { deleteSelected() }

        loadKeep()
    }

    override fun onResume() {
        super.onResume()
        loadKeep()
    }

    private fun loadKeep() {
        val list = keepManager.getKeepList()
        adapter = SelectablePhotoAdapter(list, isSelectionMode, selectedIds) { count ->
            updateSelectionInfo(count)
        }
        recyclerKeep.adapter = adapter
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

        loadKeep()
    }

    private fun selectAll() {
        val allPhotos = keepManager.getKeepList()
        selectedIds.clear()
        for (photo in allPhotos) {
            selectedIds.add(photo.id)
        }
        loadKeep()
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

    private fun deleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show()
            return
        }

        val allPhotos = keepManager.getKeepList()
        for (photo in allPhotos) {
            if (selectedIds.contains(photo.id)) {
                keepManager.removeFromKeep(photo)
            }
        }

        Toast.makeText(this, "已从保留站移除 ${selectedIds.size} 张照片", Toast.LENGTH_SHORT).show()
        selectedIds.clear()
        toggleSelectionMode()
        loadKeep()
    }
}
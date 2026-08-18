package com.example.manualalbumcleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerAlbums: RecyclerView
    private lateinit var btnTrash: ExtendedFloatingActionButton
    private lateinit var btnKeep: ExtendedFloatingActionButton
    private lateinit var btnLayoutToggle: MaterialButton
    private val REQUEST_PERMISSION = 1001
    private var isGridView = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerAlbums = findViewById(R.id.recyclerAlbums)
        btnTrash = findViewById(R.id.btnTrash)
        btnKeep = findViewById(R.id.btnKeep)
        btnLayoutToggle = findViewById(R.id.btnLayoutToggle)

        recyclerAlbums.layoutManager = GridLayoutManager(this, 2)

        btnTrash.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }

        btnKeep.setOnClickListener {
            startActivity(Intent(this, KeepActivity::class.java))
        }

        btnLayoutToggle.setOnClickListener {
            toggleLayout()
        }

        checkPermissionAndLoad()
    }

    private fun toggleLayout() {
        isGridView = !isGridView
        if (isGridView) {
            recyclerAlbums.layoutManager = GridLayoutManager(this, 2)
            btnLayoutToggle.text = getString(R.string.list_view)
            btnLayoutToggle.setIconResource(R.drawable.ic_list)
        } else {
            recyclerAlbums.layoutManager = LinearLayoutManager(this)
            btnLayoutToggle.text = getString(R.string.grid_view)
            btnLayoutToggle.setIconResource(R.drawable.ic_grid)
        }
        loadAlbums()
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission()) {
            loadAlbums()
        }
    }

    private fun checkPermissionAndLoad() {
        if (hasPermission()) {
            loadAlbums()
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSION)
        }
    }

    private fun hasPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadAlbums()
        } else {
            Toast.makeText(this, "需要存储权限才能访问相册", Toast.LENGTH_LONG).show()
        }
    }

    private fun loadAlbums() {
        val albums = mutableMapOf<String, AlbumInfo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA
        )

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketId = cursor.getString(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "未知相册"
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                val album = albums.getOrPut(bucketId) {
                    AlbumInfo(bucketId, bucketName, uri, 0)
                }
                album.count++
            }
        }

        val albumList = albums.values.toList()
        recyclerAlbums.adapter = AlbumAdapter(albumList, isGridView) { album ->
            val intent = Intent(this, PhotoSwipeActivity::class.java)
            intent.putExtra("bucketId", album.bucketId)
            intent.putExtra("bucketName", album.bucketName)
            startActivity(intent)
        }
    }

    data class AlbumInfo(
        val bucketId: String,
        val bucketName: String,
        val coverUri: Uri,
        var count: Int
    )
}

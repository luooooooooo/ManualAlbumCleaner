package com.example.manualalbumcleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.example.manualalbumcleaner.widget.PinchGridRecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerAlbums: PinchGridRecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var btnTrash: ExtendedFloatingActionButton
    private lateinit var btnLayoutToggle: MaterialButton
    private lateinit var adapter: AlbumAdapter
    private lateinit var trashManager: TrashManager

    private var albumList: List<AlbumInfo> = emptyList()
    private var isGridView = true
    private var gridColumns = DEFAULT_COLUMNS

    private val uiPrefs by lazy { getSharedPreferences("ui_prefs", MODE_PRIVATE) }

    private val REQUEST_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        recyclerAlbums = findViewById(R.id.recyclerAlbums)
        emptyState = findViewById(R.id.emptyState)
        btnTrash = findViewById(R.id.btnTrash)
        btnLayoutToggle = findViewById(R.id.btnLayoutToggle)
        trashManager = TrashManager(this)

        gridColumns = uiPrefs.getInt(KEY_GRID_COLUMNS, DEFAULT_COLUMNS)
            .coerceIn(DEFAULT_COLUMNS, MAX_COLUMNS)

        recyclerAlbums.layoutManager = GridLayoutManager(this, gridColumns)
        recyclerAlbums.setColumns(gridColumns)
        recyclerAlbums.onColumnsChanged = { count ->
            gridColumns = count
            uiPrefs.edit().putInt(KEY_GRID_COLUMNS, count).apply()
            adapter.setColumns(count)
        }

        adapter = AlbumAdapter(emptyList(), isGridView, gridColumns) { album ->
            val intent = Intent(this, PhotoSwipeActivity::class.java)
            intent.putExtra("bucketId", album.bucketId)
            intent.putExtra("bucketName", album.bucketName)
            startActivity(intent)
        }
        recyclerAlbums.adapter = adapter

        btnTrash.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        btnLayoutToggle.setOnClickListener { toggleLayout() }

        applyWindowInsets()
        checkPermissionAndLoad()
    }

    private fun applyWindowInsets() {
        val baseRecyclerBottom = recyclerAlbums.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = bars.top)
            recyclerAlbums.updatePadding(bottom = baseRecyclerBottom + bars.bottom)
            btnTrash.translationY = -bars.bottom.toFloat()
            emptyState.updatePadding(bottom = bars.bottom)
            insets
        }
    }

    private fun toggleLayout() {
        isGridView = !isGridView
        if (isGridView) {
            recyclerAlbums.layoutManager = GridLayoutManager(this, gridColumns)
            recyclerAlbums.setColumns(gridColumns)
            btnLayoutToggle.setIconResource(R.drawable.ic_list)
            btnLayoutToggle.contentDescription = getString(R.string.grid_view)
        } else {
            recyclerAlbums.layoutManager = LinearLayoutManager(this)
            btnLayoutToggle.setIconResource(R.drawable.ic_grid)
            btnLayoutToggle.contentDescription = getString(R.string.list_view)
        }
        adapter.setData(albumList, isGridView)
        recyclerAlbums.scrollToPosition(0)
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
            MediaStore.Images.Media.DATA,
            volumeColumnName()
        ).filterNotNull().toTypedArray()

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
            val volumeColumn = cursor.getColumnIndex(volumeColumnName() ?: "")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketId = cursor.getString(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "未知相册"
                val volume = if (volumeColumn >= 0) cursor.getString(volumeColumn) else null
                val uri = imageUriOf(id, volume)

                val album = albums.getOrPut(bucketId) {
                    AlbumInfo(bucketId, bucketName, uri, 0)
                }
                album.count++
            }
        }

        albumList = albums.values.toList()
        adapter.setData(albumList, isGridView)
        recyclerAlbums.isVisible = albumList.isNotEmpty()
        emptyState.isVisible = albumList.isEmpty()
        updateHeader()
        updateTrashButton()
    }

    private fun updateHeader() {
        toolbar.subtitle = if (albumList.isEmpty()) {
            null
        } else {
            getString(R.string.album_count, albumList.size)
        }
    }

    private fun updateTrashButton() {
        val count = trashManager.getTrashList().size
        btnTrash.text = if (count > 0) {
            getString(R.string.trash_count_short, count)
        } else {
            getString(R.string.trash)
        }
    }

    data class AlbumInfo(
        val bucketId: String,
        val bucketName: String,
        val coverUri: Uri,
        var count: Int
    )

    private companion object {
        /** 默认两列，双指捏合可放大到 4~5 列 */
        const val DEFAULT_COLUMNS = 2
        const val MAX_COLUMNS = 5
        const val KEY_GRID_COLUMNS = "grid_columns"
    }
}

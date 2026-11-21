package com.example.manualalbumcleaner

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.GridLayoutManager
import com.example.manualalbumcleaner.widget.PinchGridRecyclerView
import com.google.android.material.button.MaterialButton

/**
 * 选择起始照片页：把相册里的照片铺成缩略图墙，双指可缩放缩略图大小，
 * 点任意一张即从该张开始浏览；也可以直接点「从头开始」。
 */
class PhotoPickerActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var header: LinearLayout
    private lateinit var grid: PinchGridRecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var emptyContainer: LinearLayout
    private lateinit var tvAlbumName: TextView
    private lateinit var tvPickerHint: TextView

    private lateinit var trashManager: TrashManager

    private val photos = mutableListOf<PhotoItem>()
    private var bucketId: String = ""
    private var albumName: String = ""

    companion object {
        private const val DEFAULT_COLUMNS = 4
        private const val MIN_COLUMNS = 2
        private const val MAX_COLUMNS = 6
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_picker)

        root = findViewById(R.id.pickerRoot)
        header = findViewById(R.id.pickerHeader)
        grid = findViewById(R.id.gridPhotos)
        progress = findViewById(R.id.progress)
        emptyContainer = findViewById(R.id.emptyContainer)
        tvAlbumName = findViewById(R.id.tvAlbumName)
        tvPickerHint = findViewById(R.id.tvPickerHint)

        trashManager = TrashManager(this)

        albumName = intent.getStringExtra("bucketName").orEmpty()
        bucketId = intent.getStringExtra("bucketId").orEmpty()
        tvAlbumName.text = albumName

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnStartFirst).setOnClickListener { startAt(0) }

        grid.layoutManager = GridLayoutManager(this, DEFAULT_COLUMNS)
        grid.minColumns = MIN_COLUMNS
        grid.maxColumns = MAX_COLUMNS
        grid.setColumns(DEFAULT_COLUMNS)

        applyWindowInsets()

        if (bucketId.isEmpty()) {
            progress.visibility = View.GONE
            emptyContainer.visibility = View.VISIBLE
            return
        }
        loadPhotos()
    }

    private fun applyWindowInsets() {
        val headerTop = header.paddingTop
        val headerStart = header.paddingStart
        val headerEnd = header.paddingEnd
        val gridTop = grid.paddingTop
        val gridBottom = grid.paddingBottom
        val gridStart = grid.paddingStart
        val gridEnd = grid.paddingEnd
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.updatePaddingRelative(
                top = headerTop + bars.top,
                start = headerStart + bars.left,
                end = headerEnd + bars.right
            )
            grid.updatePaddingRelative(
                top = gridTop,
                bottom = gridBottom + bars.bottom,
                start = gridStart + bars.left,
                end = gridEnd + bars.right
            )
            insets
        }
    }

    /** 查询在后台线程执行，避免大相册进入时卡住界面 */
    private fun loadPhotos() {
        Thread {
            val trashIds = trashManager.getTrashIds()
            val loaded = AlbumPhotoLoader.load(contentResolver, bucketId, trashIds)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                bindPhotos(loaded)
            }
        }.start()
    }

    private fun bindPhotos(loaded: List<PhotoItem>) {
        progress.visibility = View.GONE
        photos.clear()
        photos.addAll(loaded)

        if (photos.isEmpty()) {
            grid.visibility = View.GONE
            tvPickerHint.text = getString(R.string.no_photos_in_album)
            emptyContainer.visibility = View.VISIBLE
            return
        }

        tvPickerHint.text = getString(R.string.picker_hint, photos.size)
        grid.adapter = PhotoTileAdapter(photos) { index -> startAt(index) }
    }

    private fun startAt(index: Int) {
        val photo = photos.getOrNull(index)
        val data = Intent()
        data.putExtra("startPhotoId", photo?.id ?: -1L)
        data.putExtra("startPosition", index)
        setResult(RESULT_OK, data)
        finish()
    }
}

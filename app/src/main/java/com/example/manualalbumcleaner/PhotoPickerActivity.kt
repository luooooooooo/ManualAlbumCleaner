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
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.GridLayoutManager
import com.example.manualalbumcleaner.widget.FastScrollDateBar
import com.example.manualalbumcleaner.widget.PinchGridRecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 选择起始照片页：把相册里的照片铺成缩略图墙，可筛选类型、切换时间顺序、
 * 用右侧手柄按日期快速定位；点任意一张即从该张开始浏览。
 */
class PhotoPickerActivity : AppCompatActivity() {

    private lateinit var root: View
    private lateinit var header: LinearLayout
    private lateinit var grid: PinchGridRecyclerView
    private lateinit var dateBar: FastScrollDateBar
    private lateinit var progress: ProgressBar
    private lateinit var emptyContainer: LinearLayout
    private lateinit var tvAlbumName: TextView
    private lateinit var tvPickerHint: TextView
    private lateinit var filterChips: ChipGroup
    private lateinit var btnSort: MaterialButton

    private lateinit var trashManager: TrashManager
    private val uiPrefs by lazy { getSharedPreferences("ui_prefs", MODE_PRIVATE) }

    private val photos = mutableListOf<PhotoItem>()
    private var bucketId: String = ""
    private var albumName: String = ""
    private var filter: PhotoFilter = PhotoFilter.ALL
    private var sort: SortOrder = SortOrder.NEWEST_FIRST
    private var loadToken = 0

    private val dateFormat = SimpleDateFormat("yyyy/M/d", Locale.CHINA)

    companion object {
        private const val DEFAULT_COLUMNS = 4
        private const val MIN_COLUMNS = 2
        private const val MAX_COLUMNS = 6
        private const val KEY_SORT = "sort_order"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_picker)

        root = findViewById(R.id.pickerRoot)
        header = findViewById(R.id.pickerHeader)
        grid = findViewById(R.id.gridPhotos)
        dateBar = findViewById(R.id.dateBar)
        progress = findViewById(R.id.progress)
        emptyContainer = findViewById(R.id.emptyContainer)
        tvAlbumName = findViewById(R.id.tvAlbumName)
        tvPickerHint = findViewById(R.id.tvPickerHint)
        filterChips = findViewById(R.id.filterChips)
        btnSort = findViewById(R.id.btnSort)

        trashManager = TrashManager(this)

        albumName = intent.getStringExtra("bucketName").orEmpty()
        bucketId = intent.getStringExtra("bucketId").orEmpty()
        tvAlbumName.text = albumName

        sort = parseSort(uiPrefs.getString(KEY_SORT, null))
        updateSortButton()

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnStartFirst).setOnClickListener { finishWith(0) }

        btnSort.setOnClickListener {
            sort = if (sort == SortOrder.NEWEST_FIRST) SortOrder.OLDEST_FIRST else SortOrder.NEWEST_FIRST
            uiPrefs.edit().putString(KEY_SORT, sort.name).apply()
            updateSortButton()
            reload()
        }

        filterChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val newFilter = when (checkedIds.firstOrNull()) {
                R.id.chipJpg -> PhotoFilter.JPG
                R.id.chipRaw -> PhotoFilter.RAW
                else -> PhotoFilter.ALL
            }
            if (newFilter != filter) {
                filter = newFilter
                reload()
            }
        }

        grid.layoutManager = GridLayoutManager(this, DEFAULT_COLUMNS)
        grid.minColumns = MIN_COLUMNS
        grid.maxColumns = MAX_COLUMNS
        grid.setColumns(DEFAULT_COLUMNS)

        dateBar.contentDescription = getString(R.string.fast_scroll_hint)
        dateBar.labelProvider = { index ->
            val photo = photos.getOrNull(index)
            if (photo != null && photo.dateMillis > 0) dateFormat.format(Date(photo.dateMillis)) else null
        }
        dateBar.onSeek = { index ->
            (grid.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(index, 0)
        }
        dateBar.recyclerView = grid

        applyWindowInsets()

        if (bucketId.isEmpty()) {
            progress.visibility = View.GONE
            emptyContainer.visibility = View.VISIBLE
            return
        }
        reload()
    }

    private fun parseSort(value: String?): SortOrder =
        if (value == SortOrder.OLDEST_FIRST.name) SortOrder.OLDEST_FIRST else SortOrder.NEWEST_FIRST

    private fun updateSortButton() {
        btnSort.text = getString(
            if (sort == SortOrder.NEWEST_FIRST) R.string.sort_newest else R.string.sort_oldest
        )
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

    /** 查询在后台线程执行，避免大相册进入或切换筛选时卡住界面 */
    private fun reload() {
        val token = ++loadToken
        progress.visibility = View.VISIBLE
        Thread {
            val trashIds = trashManager.getTrashIds()
            val loaded = AlbumPhotoLoader.load(contentResolver, bucketId, trashIds, filter, sort)
            runOnUiThread {
                if (token != loadToken || isFinishing || isDestroyed) return@runOnUiThread
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
            emptyContainer.visibility = View.VISIBLE
            tvPickerHint.text = getString(
                if (filter == PhotoFilter.ALL) R.string.no_photos_in_album else R.string.filter_empty
            )
            return
        }

        grid.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        tvPickerHint.text = getString(R.string.picker_hint, photos.size)
        grid.adapter = PhotoTileAdapter(photos) { index -> finishWith(index) }
        grid.scrollToPosition(0)
        dateBar.refresh()
    }

    private fun finishWith(index: Int) {
        val photo = photos.getOrNull(index)
        val data = Intent()
        data.putExtra("startPhotoId", photo?.id ?: -1L)
        data.putExtra("startPosition", index)
        data.putExtra("photoFilter", filter.name)
        data.putExtra("sortOrder", sort.name)
        setResult(RESULT_OK, data)
        finish()
    }
}

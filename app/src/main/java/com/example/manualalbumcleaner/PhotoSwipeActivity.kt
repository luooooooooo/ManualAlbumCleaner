package com.example.manualalbumcleaner

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.viewpager2.widget.ViewPager2
import com.example.manualalbumcleaner.widget.SwipeUpToTrashLayout
import com.google.android.material.button.MaterialButton
import kotlin.math.abs

/**
 * 相册浏览页：左右滑动由 ViewPager2 负责翻看上一张 / 下一张，
 * 向上拖动由 [SwipeUpToTrashLayout] 接管，超过阈值即移入回收站。
 */
class PhotoSwipeActivity : AppCompatActivity() {

    private lateinit var swipeLayout: SwipeUpToTrashLayout
    private lateinit var pager: ViewPager2
    private lateinit var tvAlbumName: TextView
    private lateinit var tvPageIndicator: TextView
    private lateinit var topBar: LinearLayout
    private lateinit var bottomBar: LinearLayout
    private lateinit var emptyContainer: LinearLayout

    private lateinit var trashManager: TrashManager
    private lateinit var adapter: PhotoPagerAdapter

    private val photos = mutableListOf<PhotoItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_swipe)

        trashManager = TrashManager(this)

        swipeLayout = findViewById(R.id.swipeLayout)
        pager = findViewById(R.id.pagerPhotos)
        tvAlbumName = findViewById(R.id.tvAlbumName)
        tvPageIndicator = findViewById(R.id.tvPageIndicator)
        topBar = findViewById(R.id.topBar)
        bottomBar = findViewById(R.id.bottomBar)
        emptyContainer = findViewById(R.id.emptyContainer)

        val albumName = intent.getStringExtra("bucketName").orEmpty()
        tvAlbumName.text = albumName

        findViewById<MaterialButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnBackToAlbums).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnOpenTrash).setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }

        applyWindowInsets()

        val bucketId = intent.getStringExtra("bucketId") ?: return
        loadPhotos(bucketId)

        setUpPager()
        setUpSwipeGesture()

        if (photos.isEmpty()) {
            showEmptyState()
        } else {
            updateIndicator()
            animateContentIn()
        }
    }

    private fun applyWindowInsets() {
        val baseTop = topBar.paddingTop
        val baseBottom = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(swipeLayout) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.updatePadding(top = baseTop + bars.top)
            bottomBar.updatePadding(bottom = baseBottom + bars.bottom)
            emptyContainer.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun loadPhotos(bucketId: String) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            volumeColumnName()
        ).filterNotNull().toTypedArray()
        val selection = "${MediaStore.Images.Media.BUCKET_ID} = ?"
        val selectionArgs = arrayOf(bucketId)

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val volumeColumn = cursor.getColumnIndex(volumeColumnName() ?: "")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: ""
                val bId = cursor.getString(bucketIdColumn) ?: ""
                val bName = cursor.getString(bucketNameColumn) ?: ""
                val volume = if (volumeColumn >= 0) cursor.getString(volumeColumn) else null
                // 已在回收站里的照片不再出现在浏览队列中
                if (!trashManager.isInTrash(id)) {
                    photos.add(PhotoItem(id, imageUriOf(id, volume), name, bId, bName))
                }
            }
        }
    }

    private fun setUpPager() {
        adapter = PhotoPagerAdapter(photos)
        pager.adapter = adapter
        pager.offscreenPageLimit = 2
        // 相邻页略微缩小并淡化，翻页时像画廊一样有层次
        pager.setPageTransformer { page, position ->
            val offset = abs(position).coerceAtMost(1f)
            page.alpha = 1f - 0.4f * offset
            val scale = 1f - 0.07f * offset
            page.scaleX = scale
            page.scaleY = scale
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateIndicator()
            }
        })
    }

    private fun setUpSwipeGesture() {
        swipeLayout.dragTarget = pager
        swipeLayout.trashTarget = findViewById(R.id.trashTarget)
        swipeLayout.dragEnabled = photos.isNotEmpty()
        swipeLayout.onDragStart = {
            pager.isUserInputEnabled = false
        }
        swipeLayout.onDragCancel = {
            pager.isUserInputEnabled = true
        }
        swipeLayout.onTrashed = {
            pager.isUserInputEnabled = true
            trashCurrentPhoto()
        }
    }

    private fun updateIndicator() {
        if (photos.isEmpty()) return
        val index = pager.currentItem.coerceIn(0, photos.lastIndex)
        tvPageIndicator.text = getString(R.string.page_indicator, index + 1, photos.size)
    }

    /** 把当前这一张移入回收站并从浏览队列里移除 */
    private fun trashCurrentPhoto() {
        val index = pager.currentItem
        if (index !in photos.indices) return

        val photo = photos[index]
        trashManager.addToTrash(photo)
        photos.removeAt(index)
        adapter.notifyItemRemoved(index)

        if (photos.isEmpty()) {
            showEmptyState()
            return
        }

        if (pager.currentItem >= photos.size) {
            pager.setCurrentItem(photos.lastIndex, false)
        }
        updateIndicator()
    }

    private fun animateContentIn() {
        pager.alpha = 0f
        pager.animate().alpha(1f).setDuration(220).start()
    }

    private fun showEmptyState() {
        swipeLayout.dragEnabled = false
        pager.visibility = View.GONE
        bottomBar.visibility = View.GONE
        tvPageIndicator.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE

        emptyContainer.alpha = 0f
        emptyContainer.translationY = 40f
        emptyContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(280)
            .start()
    }
}

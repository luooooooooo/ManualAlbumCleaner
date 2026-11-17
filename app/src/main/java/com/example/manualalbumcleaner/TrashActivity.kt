package com.example.manualalbumcleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class TrashActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var btnSelect: MaterialButton
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var recyclerTrash: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var selectionActions: View
    private lateinit var btnRestoreSelected: MaterialButton
    private lateinit var btnDeleteSelected: MaterialButton
    private lateinit var trashManager: TrashManager
    private lateinit var adapter: SelectablePhotoAdapter

    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<Long>()

    /** 待发送的删除请求队列：系统一次只弹一个删除确认框，多批时串行发送 */
    private val pendingBatches = mutableListOf<List<PhotoItem>>()
    private var currentBatch: List<PhotoItem>? = null
    private var deletedCount = 0
    private var isDeleting = false

    companion object {
        private const val TAG = "TrashActivity"
        private const val REQUEST_DELETE_PERMISSION = 2001
        private const val REQUEST_WRITE_PERMISSION = 2002

        /**
         * 单次删除请求最多携带的照片数量。Uri 会随 ClipData 跨进程传输，
         * 数量过多会超过 Binder 事务上限（约 1MB）而失败。
         */
        private const val DELETE_BATCH_SIZE = 200
    }

    /** 单张照片在 MediaStore 中的状态 */
    private sealed class MediaState {
        data class Alive(val photo: PhotoItem) : MediaState()
        object Gone : MediaState()
        object Inaccessible : MediaState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_trash)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        btnSelect = findViewById(R.id.btnSelect)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        recyclerTrash = findViewById(R.id.recyclerTrash)
        emptyState = findViewById(R.id.emptyState)
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

        applyWindowInsets()
        loadTrash()
    }

    private fun applyWindowInsets() {
        val baseRecyclerBottom = recyclerTrash.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.updatePadding(top = bars.top)
            recyclerTrash.updatePadding(bottom = baseRecyclerBottom + bars.bottom + 88)
            selectionActions.translationY = -bars.bottom.toFloat()
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        loadTrash()
    }

    private fun loadTrash() {
        val list = trashManager.getTrashList()
        adapter.setPhotos(list)
        val isEmpty = list.isEmpty()
        emptyState.isVisible = isEmpty
        recyclerTrash.isVisible = !isEmpty

        if (isEmpty) {
            isSelectionMode = false
            selectedIds.clear()
            adapter.setSelectionMode(false)
        }
        applySelectionModeUi()
        updateSelectionInfo(selectedIds.size)
    }

    private fun applySelectionModeUi() {
        btnSelect.text = getString(if (isSelectionMode) R.string.cancel else R.string.select)
        btnSelectAll.isVisible = isSelectionMode
        selectionActions.isVisible = isSelectionMode && adapter.itemCount > 0
    }

    private fun toggleSelectionMode() {
        if (isSelectionMode) exitSelectionMode() else enterSelectionMode()
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        selectedIds.clear()
        adapter.setSelectionMode(true)
        applySelectionModeUi()
        updateSelectionInfo(0)
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        adapter.setSelectionMode(false)
        applySelectionModeUi()
        updateSelectionInfo(0)
    }

    private fun selectAll() {
        selectedIds.clear()
        for (photo in trashManager.getTrashList()) {
            selectedIds.add(photo.id)
        }
        adapter.notifyDataSetChanged()
        updateSelectionInfo(selectedIds.size)
    }

    private fun updateSelectionInfo(count: Int) {
        toolbar.subtitle = when {
            isSelectionMode && count > 0 -> getString(R.string.selected_count, count)
            isSelectionMode -> getString(R.string.tap_to_select)
            adapter.itemCount > 0 -> getString(R.string.photo_count, adapter.itemCount)
            else -> null
        }
    }

    private fun restoreSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show()
            return
        }

        var restored = 0
        for (photo in trashManager.getTrashList()) {
            if (selectedIds.contains(photo.id)) {
                trashManager.removeFromTrash(photo)
                restored++
            }
        }

        Toast.makeText(this, "已恢复 $restored 张照片", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
        loadTrash()
    }

    // region 彻底删除

    private fun deleteSelected() {
        if (isDeleting) return
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "请先选择照片", Toast.LENGTH_SHORT).show()
            return
        }

        val photosToDelete = trashManager.getTrashList().filter { selectedIds.contains(it.id) }
        if (photosToDelete.isEmpty()) {
            Toast.makeText(this, "所选照片已不在回收站", Toast.LENGTH_SHORT).show()
            return
        }

        isDeleting = true
        btnDeleteSelected.isEnabled = false
        Toast.makeText(this, "正在检查 ${photosToDelete.size} 张照片…", Toast.LENGTH_SHORT).show()

        // 删除前逐张校验：MediaStore 中已不存在的记录（例如在系统相册或其他清理工具里
        // 被删掉、被云同步移走）如果一起塞进 createDeleteRequest，系统会整批抛
        // IllegalArgumentException，导致"删除请求失败"。
        Thread {
            val alive = mutableListOf<PhotoItem>()
            val gone = mutableListOf<PhotoItem>()
            var inaccessible = 0

            for (photo in photosToDelete) {
                when (val state = resolveMediaState(photo)) {
                    is MediaState.Alive -> alive.add(state.photo)
                    MediaState.Gone -> gone.add(photo)
                    MediaState.Inaccessible -> inaccessible++
                }
            }

            runOnUiThread {
                btnDeleteSelected.isEnabled = true

                if (gone.isNotEmpty()) {
                    for (photo in gone) {
                        trashManager.removeFromTrash(photo)
                    }
                    Toast.makeText(
                        this,
                        "${gone.size} 张照片已不在设备中，已从回收站移除",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (inaccessible > 0) {
                    Toast.makeText(
                        this,
                        "有 $inaccessible 张照片暂时无法读取，已保留在回收站，未参与删除",
                        Toast.LENGTH_LONG
                    ).show()
                }

                if (alive.isEmpty()) {
                    isDeleting = false
                    loadTrash()
                    return@runOnUiThread
                }
                startDeleteRequests(alive)
            }
        }.start()
    }

    /**
     * 查询照片在 MediaStore 中的真实状态，并用查询到的真实 volume 重建 Uri。
     *
     * 回收站里保存的 Uri 是 content://media/external/images/media/<id>，其中 external 是
     * 聚合卷，_ID 在内部存储和 SD 卡之间会重复；直接拿它去删除可能命中错误条目或被系统拒绝，
     * 因此这里以查询结果为准确认 id 与 volume。
     */
    private fun resolveMediaState(photo: PhotoItem): MediaState {
        // 优先连同 volume 一起查询，以便修正旧记录里使用聚合卷的 Uri；
        // 若该查询方式不被设备支持，再退回只查 _ID 的查询。
        queryMediaState(photo, withVolume = true)?.let { return it }
        queryMediaState(photo, withVolume = false)?.let { return it }
        return MediaState.Inaccessible
    }

    /**
     * 查询单张照片在 MediaStore 中的状态。返回 null 表示这种方式查不了，需要换一种方式重试。
     */
    private fun queryMediaState(photo: PhotoItem, withVolume: Boolean): MediaState? {
        val useVolumeColumn = withVolume && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = if (useVolumeColumn) {
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.VOLUME_NAME)
        } else {
            arrayOf(MediaStore.MediaColumns._ID)
        }

        return try {
            val cursor = contentResolver.query(photo.uri, projection, null, null, null)
            if (cursor == null) {
                return MediaState.Gone
            }
            cursor.use { it ->
                if (!it.moveToFirst()) {
                    return MediaState.Gone
                }
                val realId = it.getLong(0)
                val volume = if (useVolumeColumn && it.columnCount > 1) it.getString(1) else null
                // 查不到真实 volume 时沿用原 Uri，交给系统去解析
                val realUri = if (volume.isNullOrEmpty()) photo.uri else imageUriOf(realId, volume)
                MediaState.Alive(photo.copy(id = realId, uri = realUri))
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "无权访问 ${photo.uri}", e)
            MediaState.Inaccessible
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "查询方式不受支持 ${photo.uri}: ${e.message}")
            null
        } catch (e: Exception) {
            // 查询失败时无法判断照片真实状态，保守起见既不清理记录也不参与删除
            Log.w(TAG, "解析照片失败 ${photo.uri}", e)
            null
        }
    }

    /** 按 volume 分组，再按批大小切分，逐个发送系统删除请求 */
    private fun startDeleteRequests(photos: List<PhotoItem>) {
        deletedCount = 0
        pendingBatches.clear()

        photos.groupBy { it.uri.pathSegments.firstOrNull().orEmpty() }
            .values
            .forEach { group -> pendingBatches.addAll(group.chunked(DELETE_BATCH_SIZE)) }

        processNextBatch()
    }

    private fun processNextBatch() {
        if (pendingBatches.isEmpty()) {
            finishDeleting()
            return
        }

        val batch = pendingBatches.removeAt(0)
        currentBatch = batch

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, batch.map { it.uri })
                startIntentSenderForResult(
                    pendingIntent.intentSender,
                    REQUEST_DELETE_PERMISSION,
                    null, 0, 0, 0
                )
            } catch (e: Exception) {
                Log.e(TAG, "createDeleteRequest 失败，共 ${batch.size} 张", e)
                // 把系统给出的真实原因暴露给用户，避免只有一句"删除请求失败"；
                // 失败后不再继续后续批次，避免连续弹出多个系统对话框。
                val reason = e.message ?: e.javaClass.simpleName
                currentBatch = null
                pendingBatches.clear()
                finishDeleting(notifyDeleted = false)
                val message = if (deletedCount > 0) {
                    "删除请求失败：$reason（已删除 $deletedCount 张）"
                } else {
                    "删除请求失败：$reason"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } else {
            // Android 10 及以下：检查写入权限后直接删除
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
            deleteDirectly(batch)
        }
    }

    /** Android 10 及以下的直接删除 */
    private fun deleteDirectly(photos: List<PhotoItem>) {
        var failed = 0
        for (photo in photos) {
            try {
                contentResolver.delete(photo.uri, null, null)
                trashManager.removeFromTrash(photo)
                deletedCount++
            } catch (e: Exception) {
                Log.e(TAG, "直接删除失败 ${photo.uri}", e)
                failed++
            }
        }

        currentBatch = null
        if (failed > 0) {
            Toast.makeText(this, "有 $failed 张照片删除失败", Toast.LENGTH_SHORT).show()
        }
        processNextBatch()
    }

    private fun finishDeleting(notifyDeleted: Boolean = true) {
        if (notifyDeleted && deletedCount > 0) {
            Toast.makeText(this, "已彻底删除 $deletedCount 张照片", Toast.LENGTH_SHORT).show()
        }
        isDeleting = false
        btnDeleteSelected.isEnabled = true
        currentBatch = null
        pendingBatches.clear()
        if (isSelectionMode) {
            exitSelectionMode()
        }
        loadTrash()
    }

    // endregion

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DELETE_PERMISSION) return

        val batch = currentBatch
        if (batch == null) {
            // 进程被系统回收后恢复等场景，没有待处理的批次，直接收尾
            finishDeleting()
            return
        }

        if (resultCode == RESULT_OK) {
            // 用户已确认，系统已完成删除
            for (photo in batch) {
                trashManager.removeFromTrash(photo)
            }
            deletedCount += batch.size
            currentBatch = null
            processNextBatch()
        } else {
            val remaining = batch.size + pendingBatches.sumOf { it.size }
            currentBatch = null
            pendingBatches.clear()
            finishDeleting(notifyDeleted = false)
            val message = if (deletedCount > 0) {
                "已取消，已删除 $deletedCount 张，其余 $remaining 张仍保留在回收站"
            } else {
                "已取消删除，照片仍保留在回收站"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_WRITE_PERMISSION) return

        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (granted) {
            currentBatch?.let { deleteDirectly(it) }
        } else {
            Toast.makeText(this, "需要存储写入权限才能删除照片", Toast.LENGTH_SHORT).show()
            currentBatch = null
            processNextBatch()
        }
    }
}

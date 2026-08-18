package com.example.manualalbumcleaner

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.min

class PhotoSwipeActivity : AppCompatActivity() {

    private lateinit var cardPhoto: CardView
    private lateinit var imgPhoto: ImageView
    private lateinit var tvHint: TextView
    private lateinit var emptyContainer: LinearLayout
    private lateinit var trashManager: TrashManager
    private lateinit var keepManager: KeepManager

    private lateinit var keepArea: LinearLayout
    private lateinit var deleteArea: LinearLayout
    private lateinit var iconKeep: ImageView
    private lateinit var labelKeep: TextView
    private lateinit var iconDelete: ImageView
    private lateinit var labelDelete: TextView

    private val photos = mutableListOf<PhotoItem>()
    private var currentIndex = 0
    private var isAnimating = false

    private var downX = 0f
    private var downY = 0f
    private var cardStartX = 0f
    private var cardStartY = 0f

    private val SWIPE_THRESHOLD = 200f
    private val ROTATION_FACTOR = 20f
    private val MAX_ALPHA_DISTANCE = 350f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_swipe)

        cardPhoto = findViewById(R.id.cardPhoto)
        imgPhoto = findViewById(R.id.imgPhoto)
        tvHint = findViewById(R.id.tvHint)
        emptyContainer = findViewById(R.id.emptyContainer)
        trashManager = TrashManager(this)
        keepManager = KeepManager(this)

        keepArea = findViewById(R.id.keepArea)
        deleteArea = findViewById(R.id.deleteArea)
        iconKeep = findViewById(R.id.iconKeep)
        labelKeep = findViewById(R.id.labelKeep)
        iconDelete = findViewById(R.id.iconDelete)
        labelDelete = findViewById(R.id.labelDelete)

        val bucketId = intent.getStringExtra("bucketId") ?: return
        loadPhotos(bucketId)

        if (photos.isEmpty()) {
            showEmptyState()
        } else {
            setupTouchListener()
            showCurrentPhoto()
        }
    }

    private fun loadPhotos(bucketId: String) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
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
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: ""
                val bId = cursor.getString(bucketIdColumn) ?: ""
                val bName = cursor.getString(bucketNameColumn) ?: ""
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                // Skip photos already in keep or trash
                if (!keepManager.isKept(id) && !trashManager.isInTrash(id)) {
                    photos.add(PhotoItem(id, uri, name, bId, bName))
                }
            }
        }
    }

    private fun setupTouchListener() {
        cardPhoto.setOnTouchListener { _, event ->
            if (isAnimating) return@setOnTouchListener true

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    cardStartX = cardPhoto.translationX
                    cardStartY = cardPhoto.translationY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY

                    cardPhoto.translationX = cardStartX + deltaX
                    cardPhoto.translationY = cardStartY + deltaY * 0.2f
                    cardPhoto.rotation = deltaX / ROTATION_FACTOR

                    updateVisualFeedback(deltaX)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - downX
                    handleRelease(deltaX)
                    true
                }

                else -> false
            }
        }
    }

    private fun updateVisualFeedback(deltaX: Float) {
        val absDx = abs(deltaX)
        val progress = min(1f, absDx / MAX_ALPHA_DISTANCE)
        val scale = 0.7f + (0.3f * progress)

        if (deltaX < 0) {
            keepArea.alpha = progress
            keepArea.scaleX = scale
            keepArea.scaleY = scale
            deleteArea.alpha = 0f
            iconKeep.alpha = 1f
            labelKeep.alpha = 1f
            iconDelete.alpha = 0f
            labelDelete.alpha = 0f
        } else {
            deleteArea.alpha = progress
            deleteArea.scaleX = scale
            deleteArea.scaleY = scale
            keepArea.alpha = 0f
            iconKeep.alpha = 0f
            labelKeep.alpha = 0f
            iconDelete.alpha = 1f
            labelDelete.alpha = 1f
        }
    }

    private fun handleRelease(deltaX: Float) {
        isAnimating = true

        when {
            deltaX < -SWIPE_THRESHOLD -> animateKeep()
            deltaX > SWIPE_THRESHOLD -> animateDelete()
            else -> animateReturn()
        }
    }

    private fun animateReturn() {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()

        val flyBack = ObjectAnimator.ofFloat(cardPhoto, "translationX", cardPhoto.translationX, 0f)
        flyBack.duration = 200
        flyBack.interpolator = AccelerateDecelerateInterpolator()

        val rotateBack = ObjectAnimator.ofFloat(cardPhoto, "rotation", cardPhoto.rotation, 0f)
        rotateBack.duration = 200

        val areaAlphaOut = ObjectAnimator.ofFloat(keepArea, "alpha", keepArea.alpha, 0f)
        areaAlphaOut.duration = 150
        val areaAlphaOut2 = ObjectAnimator.ofFloat(deleteArea, "alpha", deleteArea.alpha, 0f)
        areaAlphaOut2.duration = 150

        flyBack.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                cardPhoto.translationY = 0f
            }
        })

        flyBack.start()
        rotateBack.start()
        areaAlphaOut.start()
        areaAlphaOut2.start()
    }

    private fun animateKeep() {
        val photo = photos[currentIndex]
        keepManager.addToKeep(photo)
        currentIndex++

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val targetX = -screenWidth * 1.2f

        val flyOut = ObjectAnimator.ofFloat(cardPhoto, "translationX", cardPhoto.translationX, targetX)
        flyOut.duration = 180
        flyOut.interpolator = AccelerateInterpolator(1.5f)

        val fadeOut = ObjectAnimator.ofFloat(cardPhoto, "alpha", 1f, 0f)
        fadeOut.duration = 150

        flyOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                resetVisualFeedback()
                if (currentIndex < photos.size) {
                    animateNextPhotoIn()
                } else {
                    showEmptyState()
                    isAnimating = false
                }
            }
        })

        flyOut.start()
        fadeOut.start()
    }

    private fun animateDelete() {
        val photo = photos[currentIndex]
        trashManager.addToTrash(photo)
        currentIndex++

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val targetX = screenWidth * 1.2f

        val flyOut = ObjectAnimator.ofFloat(cardPhoto, "translationX", cardPhoto.translationX, targetX)
        flyOut.duration = 180
        flyOut.interpolator = AccelerateInterpolator(1.5f)

        val fadeOut = ObjectAnimator.ofFloat(cardPhoto, "alpha", 1f, 0f)
        fadeOut.duration = 150

        flyOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                resetVisualFeedback()
                if (currentIndex < photos.size) {
                    animateNextPhotoIn()
                } else {
                    showEmptyState()
                    isAnimating = false
                }
            }
        })

        flyOut.start()
        fadeOut.start()
    }

    private fun animateNextPhotoIn() {
        cardPhoto.translationX = 0f
        cardPhoto.translationY = 300f
        cardPhoto.scaleX = 0.9f
        cardPhoto.scaleY = 0.9f
        cardPhoto.alpha = 0f

        showCurrentPhoto()

        val flyIn = ObjectAnimator.ofFloat(cardPhoto, "translationY", 300f, 0f)
        flyIn.duration = 200
        flyIn.interpolator = AccelerateDecelerateInterpolator()

        val scaleIn = ObjectAnimator.ofFloat(cardPhoto, "scaleX", 0.9f, 1f)
        scaleIn.duration = 200
        scaleIn.interpolator = AccelerateDecelerateInterpolator()

        val scaleInY = ObjectAnimator.ofFloat(cardPhoto, "scaleY", 0.9f, 1f)
        scaleInY.duration = 200
        scaleInY.interpolator = AccelerateDecelerateInterpolator()

        val alphaIn = ObjectAnimator.ofFloat(cardPhoto, "alpha", 0f, 1f)
        alphaIn.duration = 150

        flyIn.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
            }
        })

        flyIn.start()
        scaleIn.start()
        scaleInY.start()
        alphaIn.start()
    }

    private fun resetVisualFeedback() {
        keepArea.alpha = 0f
        deleteArea.alpha = 0f
        iconKeep.alpha = 0f
        labelKeep.alpha = 0f
        iconDelete.alpha = 0f
        labelDelete.alpha = 0f
        cardPhoto.alpha = 1f
        cardPhoto.scaleX = 1f
        cardPhoto.scaleY = 1f
        cardPhoto.translationX = 0f
        cardPhoto.translationY = 0f
        cardPhoto.rotation = 0f
    }

    private fun showCurrentPhoto() {
        if (currentIndex >= photos.size) {
            showEmptyState()
            return
        }

        cardPhoto.visibility = View.VISIBLE
        tvHint.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE

        val photo = photos[currentIndex]
        Glide.with(this)
            .load(photo.uri)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .into(imgPhoto)

        tvHint.text = getString(R.string.photo_count, photos.size - currentIndex)
    }

    private fun showEmptyState() {
        cardPhoto.visibility = View.GONE
        tvHint.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE
        emptyContainer.alpha = 0f
        emptyContainer.translationY = 60f

        val fadeIn = ObjectAnimator.ofFloat(emptyContainer, "alpha", 0f, 1f)
        fadeIn.duration = 300

        val slideUp = ObjectAnimator.ofFloat(emptyContainer, "translationY", 60f, 0f)
        slideUp.duration = 300
        slideUp.interpolator = AccelerateDecelerateInterpolator()

        fadeIn.start()
        slideUp.start()
        isAnimating = false
    }
}

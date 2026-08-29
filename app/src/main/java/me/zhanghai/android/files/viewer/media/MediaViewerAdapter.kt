/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import coil.size.Size
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.DefaultOnImageEventListener
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.coil.fadeIn
import me.zhanghai.android.files.databinding.MediaViewerImageItemBinding
import me.zhanghai.android.files.databinding.MediaViewerVideoItemBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.asMimeType
import me.zhanghai.android.files.file.asMimeTypeOrNull
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.provider.common.AndroidFileTypeDetector
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.ui.SimpleAdapter
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.shortAnimTime
import kotlin.math.max

class MediaViewerAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val listener: (View) -> Unit,
    private val onSwipeDown: () -> Unit
) : SimpleAdapter<Path, RecyclerView.ViewHolder>() {
    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isPlayableVideo) VIEW_TYPE_VIDEO else VIEW_TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = parent.context.layoutInflater
        return when (viewType) {
            VIEW_TYPE_VIDEO -> {
                val binding = MediaViewerVideoItemBinding.inflate(inflater, parent, false)
                // A video page has nothing to zoom, so every downward drag is a dismissal. The
                // playback controls sit in the fragment layout above the pager and take their own
                // touches, so dragging the slider never reaches this. See doc 10 section 4.4.
                binding.root.onDismiss = onSwipeDown
                VideoViewHolder(binding)
            }
            else -> {
                val binding = MediaViewerImageItemBinding.inflate(inflater, parent, false)
                // Dragging a zoomed image pans it, so the page only takes the gesture while
                // whichever view is showing sits at its minimum scale. See doc 10 section 4.3.
                binding.root.canDismiss = {
                    when {
                        binding.image.isVisible ->
                            binding.image.scale <= PHOTO_VIEW_MIN_SCALE * MIN_SCALE_SLOP
                        binding.largeImage.isVisible ->
                            binding.largeImage.isReady
                                && binding.largeImage.scale <=
                                binding.largeImage.minScale * MIN_SCALE_SLOP
                        // Still loading, or failed: there is nothing to pan.
                        else -> true
                    }
                }
                binding.root.onDismiss = onSwipeDown
                ImageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val path = getItem(position)
        when (holder) {
            is ImageViewHolder -> bindImage(holder.binding, path)
            is VideoViewHolder -> bindVideo(holder, path)
            else -> throw IllegalStateException(holder.toString())
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        when (holder) {
            is ImageViewHolder -> {
                holder.binding.image.dispose()
                holder.binding.largeImage.recycle()
            }
            is VideoViewHolder -> {
                // Otherwise a pending show would fire on the recycled page.
                holder.progress.endAll()
                holder.binding.thumbnailImage.dispose()
                // The player is detached by the fragment, not here.
            }
        }
    }

    private fun bindImage(binding: MediaViewerImageItemBinding, path: Path) {
        binding.root.reset()
        binding.image.setOnPhotoTapListener { view, _, _ -> listener(view) }
        binding.largeImage.setOnClickListener(listener)
        loadImage(binding, path)
    }

    private fun bindVideo(holder: VideoViewHolder, path: Path) {
        val binding = holder.binding
        binding.root.reset()
        binding.root.setOnClickListener(listener)
        binding.playerView.isVisible = false
        binding.errorLayout.isVisible = false
        // The fragment fades the thumbnail out once the first frame is rendered, and that
        // animation may still be running when this page comes back. See plan 12 3.3.
        binding.thumbnailImage.animate().cancel()
        binding.thumbnailImage.isVisible = true
        binding.thumbnailImage.alpha = 1f
        holder.progress.begin(DelayedProgress.Reason.THUMBNAIL)
        lifecycleOwner.lifecycleScope.launch {
            val attributes = try {
                withContext(Dispatchers.IO) {
                    path.readAttributes(BasicFileAttributes::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                holder.progress.end(DelayedProgress.Reason.THUMBNAIL)
                return@launch
            }
            binding.thumbnailImage.load(path to attributes) {
                size(Size.ORIGINAL)
                fadeIn(binding.thumbnailImage.context.shortAnimTime)
                listener(
                    onSuccess = { _, _ -> holder.progress.end(DelayedProgress.Reason.THUMBNAIL) },
                    onError = { _, _ -> holder.progress.end(DelayedProgress.Reason.THUMBNAIL) }
                )
            }
        }
    }

    private fun loadImage(binding: MediaViewerImageItemBinding, path: Path) {
        binding.progress.fadeInUnsafe(true)
        binding.errorText.fadeOutUnsafe()
        binding.image.isVisible = false
        binding.largeImage.isVisible = false
        lifecycleOwner.lifecycleScope.launch {
            val imageInfo = try {
                withContext(Dispatchers.IO) { path.loadImageInfo() }
            } catch (e: Exception) {
                e.printStackTrace()
                showError(binding, e)
                return@launch
            }
            loadImageWithInfo(binding, path, imageInfo)
        }
    }

    private fun Path.loadImageInfo(): ImageInfo {
        val attributes = readAttributes(BasicFileAttributes::class.java)
        val mimeType = AndroidFileTypeDetector.getMimeType(this, attributes).asMimeType()
        val bitmapOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        newInputStream().use { BitmapFactory.decodeStream(it, null, bitmapOptions) }
        // SubsamplingScaleImageView cannot find the EXIF of our content:// uri for every format —
        // HEIC comes out sideways — so we read it here and tell it the angle. See doc 13 section 8.
        val rotationDegrees = try {
            newInputStream().use { ExifInterface(it).rotationDegrees }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
        return ImageInfo(
            attributes, bitmapOptions.outWidth, bitmapOptions.outHeight,
            bitmapOptions.outMimeType?.asMimeTypeOrNull() ?: mimeType, rotationDegrees
        )
    }

    private fun loadImageWithInfo(
        binding: MediaViewerImageItemBinding,
        path: Path,
        imageInfo: ImageInfo
    ) {
        if (!imageInfo.shouldUseLargeImageView) {
            binding.image.apply {
                isVisible = true
                load(path to imageInfo.attributes) {
                    size(Size.ORIGINAL)
                    fadeIn(context.shortAnimTime)
                    listener(
                        onSuccess = { _, _ -> binding.progress.fadeOutUnsafe() },
                        onError = { _, result -> showError(binding, result.throwable) }
                    )
                }
            }
        } else {
            binding.largeImage.apply {
                setDoubleTapZoomDuration(300)
                orientation = imageInfo.rotationDegrees
                // Otherwise OnImageEventListener.onReady() is never called.
                isVisible = true
                alpha = 0f
                setOnImageEventListener(object : DefaultOnImageEventListener() {
                    override fun onReady() {
                        setDoubleTapZoomScale(binding.largeImage.cropScale)
                        binding.progress.fadeOutUnsafe()
                        binding.largeImage.fadeInUnsafe(true)
                    }

                    override fun onImageLoadError(e: Exception) {
                        e.printStackTrace()
                        showError(binding, e)
                    }
                })
                setImageRestoringSavedState(ImageSource.uri(path.fileProviderUri))
            }
        }
    }

    private val ImageInfo.shouldUseLargeImageView: Boolean
        get() {
            // See BitmapFactory.cpp encodedFormatToString()
            if (mimeType == MimeType.IMAGE_GIF) {
                return false
            }
            if (width <= 0 || height <= 0) {
                return false
            }
            // 4 bytes per pixel for ARGB_8888.
            if (width * height * 4 > LARGE_IMAGE_BITMAP_SIZE) {
                return true
            }
            if (width > 2048 || height > 2048) {
                val ratio = width.toFloat() / height
                if (ratio < 0.5 || ratio > 2) {
                    return true
                }
            }
            return false
        }

    private val SubsamplingScaleImageView.cropScale: Float
        get() {
            val viewWidth = (width - paddingLeft - paddingRight)
            val viewHeight = (height - paddingTop - paddingBottom)
            val orientation = appliedOrientation
            val rotated90Or270 = orientation == SubsamplingScaleImageView.ORIENTATION_90
                || orientation == SubsamplingScaleImageView.ORIENTATION_270
            val imageWidth = if (rotated90Or270) sHeight else sWidth
            val imageHeight = if (rotated90Or270) sWidth else sHeight
            return max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        }

    private fun showError(binding: MediaViewerImageItemBinding, throwable: Throwable) {
        binding.progress.fadeOutUnsafe()
        binding.errorText.text = throwable.toString()
        binding.errorText.fadeInUnsafe(true)
        binding.image.isVisible = false
        binding.largeImage.isVisible = false
    }

    companion object {
        private const val VIEW_TYPE_IMAGE = 0
        private const val VIEW_TYPE_VIDEO = 1

        /**
         * Above this a photo goes to the tiled view instead of becoming one whole bitmap.
         *
         * The hard limit is 100 MB (@see android.graphics.RecordingCanvas#MAX_BITMAP_SIZE), but
         * that is what a Canvas can draw at all, not what a page can draw smoothly. Three pages
         * are alive at once (offscreenPageLimit = 1) and every swipe re-uploads them, so a 12 MP
         * phone photo — 48 MB as ARGB_8888, well under the hard limit — already makes the swipe
         * animation stutter and starves video playback on the same render thread.
         */
        private const val LARGE_IMAGE_BITMAP_SIZE = 24 * 1024 * 1024

        // PhotoView measures its scale against the fitted image, so 1 is "not zoomed".
        private const val PHOTO_VIEW_MIN_SCALE = 1f
        // Leeway for comparing floating point scales.
        private const val MIN_SCALE_SLOP = 1.01f
    }

    class ImageViewHolder(val binding: MediaViewerImageItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    class VideoViewHolder(val binding: MediaViewerVideoItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        /** Thumbnail loading and buffering share one indicator, see spec 11a section 6.1. */
        val progress = DelayedProgress(binding.progress)
    }

    private class ImageInfo(
        val attributes: BasicFileAttributes,
        val width: Int,
        val height: Int,
        val mimeType: MimeType,
        /** 0, 90, 180 or 270 — what SubsamplingScaleImageView.setOrientation() takes. */
        val rotationDegrees: Int
    )
}

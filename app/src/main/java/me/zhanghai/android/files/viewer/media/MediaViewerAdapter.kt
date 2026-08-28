/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.graphics.BitmapFactory
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
    private val listener: (View) -> Unit
) : SimpleAdapter<Path, RecyclerView.ViewHolder>() {
    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isPlayableVideo) VIEW_TYPE_VIDEO else VIEW_TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = parent.context.layoutInflater
        return when (viewType) {
            VIEW_TYPE_VIDEO ->
                VideoViewHolder(MediaViewerVideoItemBinding.inflate(inflater, parent, false))
            else -> ImageViewHolder(MediaViewerImageItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val path = getItem(position)
        when (holder) {
            is ImageViewHolder -> bindImage(holder.binding, path)
            is VideoViewHolder -> bindVideo(holder.binding, path)
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
                holder.binding.thumbnailImage.dispose()
                // The player is detached by the fragment, not here.
            }
        }
    }

    private fun bindImage(binding: MediaViewerImageItemBinding, path: Path) {
        binding.image.setOnPhotoTapListener { view, _, _ -> listener(view) }
        binding.largeImage.setOnClickListener(listener)
        loadImage(binding, path)
    }

    private fun bindVideo(binding: MediaViewerVideoItemBinding, path: Path) {
        binding.root.setOnClickListener(listener)
        binding.playerView.isVisible = false
        binding.errorLayout.isVisible = false
        // The fragment fades the thumbnail out once the first frame is rendered, and that
        // animation may still be running when this page comes back. See plan 12 3.3.
        binding.thumbnailImage.animate().cancel()
        binding.thumbnailImage.isVisible = true
        binding.thumbnailImage.alpha = 1f
        binding.progress.fadeInUnsafe(true)
        lifecycleOwner.lifecycleScope.launch {
            val attributes = try {
                withContext(Dispatchers.IO) {
                    path.readAttributes(BasicFileAttributes::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.progress.fadeOutUnsafe()
                return@launch
            }
            binding.thumbnailImage.load(path to attributes) {
                size(Size.ORIGINAL)
                fadeIn(binding.thumbnailImage.context.shortAnimTime)
                listener(
                    onSuccess = { _, _ -> binding.progress.fadeOutUnsafe() },
                    onError = { _, _ -> binding.progress.fadeOutUnsafe() }
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
        return ImageInfo(
            attributes, bitmapOptions.outWidth, bitmapOptions.outHeight,
            bitmapOptions.outMimeType?.asMimeTypeOrNull() ?: mimeType
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
                orientation = SubsamplingScaleImageView.ORIENTATION_USE_EXIF
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
            if (width * height * 4 > MAX_BITMAP_SIZE) {
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

        // @see android.graphics.RecordingCanvas#MAX_BITMAP_SIZE
        private const val MAX_BITMAP_SIZE = 100 * 1024 * 1024
    }

    class ImageViewHolder(val binding: MediaViewerImageItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    class VideoViewHolder(val binding: MediaViewerVideoItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class ImageInfo(
        val attributes: BasicFileAttributes,
        val width: Int,
        val height: Int,
        val mimeType: MimeType
    )
}

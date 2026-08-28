/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.FilePropertiesTabItemBinding
import me.zhanghai.android.files.databinding.VideoDetailsDialogBinding
import me.zhanghai.android.files.file.FileSize
import me.zhanghai.android.files.file.format
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import java.time.Duration
import java.time.Instant

/**
 * Shows what is known about the video of the current page, see spec 11 section 7.
 *
 * This sheet only draws: the values come from the fragment through [Listener], because half of
 * them are only known once playback is ready. See plan 12 7.3.1.
 */
class VideoDetailsDialogFragment : BottomSheetDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    private lateinit var binding: VideoDetailsDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        VideoDetailsDialogBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bind(listener.getVideoDetails(args.path))
    }

    /** Called by the fragment when playback becomes ready or the file half arrives. */
    fun updateDetails() {
        if (view != null) {
            bind(listener.getVideoDetails(args.path))
        }
    }

    private fun bind(details: VideoDetails) {
        binding.itemLayout.removeAllViews()
        addItem(R.string.media_viewer_details_file_name, details.fileName)
        addItem(R.string.media_viewer_details_created_time, details.createdTimeMillis?.let {
            Instant.ofEpochMilli(it).formatLong()
        })
        addItem(
            R.string.media_viewer_details_dimensions,
            if (details.width != null && details.height != null) {
                getString(
                    R.string.file_properties_media_dimensions_format,
                    details.width, details.height
                )
            } else {
                null
            }
        )
        addItem(R.string.media_viewer_details_duration, details.durationMillis?.let {
            Duration.ofMillis(it).format()
        })
        addItem(R.string.media_viewer_details_frame_rate, details.frameRate?.let {
            getString(R.string.media_viewer_details_frame_rate_format, formatFrameRate(it))
        })
        addItem(R.string.media_viewer_details_codec, details.codec)
        addItem(R.string.media_viewer_details_bit_rate, details.bitRate?.let {
            getString(R.string.file_properties_media_bit_rate_format, it / 1000)
        })
        // Only when it is not 0, see spec 11 section 7.1.
        details.rotationDegrees?.let {
            addItem(
                R.string.media_viewer_details_rotation,
                getString(R.string.media_viewer_details_rotation_format, it)
            )
        }
        addItem(
            R.string.media_viewer_details_size,
            details.sizeBytes.takeIf { it >= 0 }
                ?.let { FileSize(it).formatHumanReadable(requireContext()) }
        )
        // Only when the file has one, see spec 11 section 7.1.
        details.location?.let {
            addItem(
                R.string.media_viewer_details_location,
                getString(
                    R.string.file_properties_media_coordinates_format, it.first, it.second
                )
            )
        }
        addItem(R.string.media_viewer_details_path, details.path)
    }

    /** A null value keeps the row and shows an em dash, see spec 11 section 7.1. */
    private fun addItem(@StringRes titleRes: Int, value: String?) {
        // The same row as the file properties dialog, so the two do not drift apart.
        val itemBinding = FilePropertiesTabItemBinding.inflate(
            binding.itemLayout.context.layoutInflater, binding.itemLayout, true
        )
        itemBinding.textInputLayout.hint = getString(titleRes)
        // Nothing here is clickable, so no drop down arrow.
        itemBinding.textInputLayout.setDropDown(false)
        itemBinding.text.setText(value ?: getString(R.string.media_viewer_details_unknown))
        itemBinding.text.setTextIsSelectable(true)
    }

    private fun formatFrameRate(frameRate: Float): String =
        if (frameRate == frameRate.toInt().toFloat()) {
            frameRate.toInt().toString()
        } else {
            String.format("%.2f", frameRate).trimEnd('0').trimEnd('.')
        }

    companion object {
        const val TAG = "VideoDetailsDialogFragment"

        fun show(path: Path, fragment: Fragment) {
            // A tag, unlike DialogFragmentExtensions.show(), so the fragment can find us again
            // when the player gets ready. See plan 12 7.3.1.
            VideoDetailsDialogFragment().putArgs(Args(path))
                .show(fragment.childFragmentManager, TAG)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs

    interface Listener {
        fun getVideoDetails(path: Path): VideoDetails
    }
}

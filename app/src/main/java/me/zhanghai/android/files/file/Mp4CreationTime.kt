/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import androidx.annotation.WorkerThread
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.Path
import me.zhanghai.android.files.provider.common.newByteChannel
import java.nio.ByteBuffer
import java.time.Instant

/**
 * Reads `creation_time` from the `mvhd` box of an ISO base media file (MP4/MOV).
 *
 * See PersonalDev/Doc/09-media-view-mode-plan.md 1.1.
 *
 * This walks the top level box chain by *seeking*, never by reading through. All 50 sample files
 * measured had `moov` near the end of the file (13KB ~ 287KB from the tail), so reading through
 * the `mdat` box would turn a ~300 byte read into a multi-hundred-kilobyte one.
 */
object Mp4CreationTime {
    // Difference between the 1904-01-01 epoch used by ISO base media files and the Unix epoch.
    private const val EPOCH_OFFSET_SECONDS = 2_082_844_800L

    // Guards against pathological or crafted files, see plan 1.1.
    private const val MAX_BOX_COUNT = 32
    private const val MAX_MOOV_SCAN_BYTES = 8 * 1024L

    // Sanity range for the resulting timestamp, so that a garbage value never reorders the list.
    private const val MIN_VALID_MILLIS = 0L
    private val MAX_VALID_MILLIS: Long
        get() = System.currentTimeMillis() + 24 * 60 * 60 * 1000L

    private const val BOX_MOOV = 0x6D6F6F76 // "moov"
    private const val BOX_MVHD = 0x6D766864 // "mvhd"

    /**
     * Returns the creation time in milliseconds since the Unix epoch, or null if it cannot be
     * determined. Never throws.
     */
    @WorkerThread
    fun read(path: Path): Long? =
        try {
            path.newByteChannel().use { channel -> readMoovCreationTime(channel, channel.size()) }
        } catch (e: Exception) {
            // A media file we cannot parse is not an error, it just has no creation time.
            null
        }

    private fun readMoovCreationTime(channel: SeekableByteChannel, fileSize: Long): Long? {
        val header = ByteBuffer.allocate(16)
        var offset = 0L
        var boxCount = 0
        while (true) {
            if (++boxCount > MAX_BOX_COUNT) {
                return null
            }
            if (offset < 0 || offset + 8 > fileSize) {
                return null
            }
            channel.position(offset)
            if (!readFully(channel, header, 8)) {
                return null
            }
            var size = header.getInt(0).toLong() and 0xFFFFFFFFL
            val type = header.getInt(4)
            var headerSize = 8L
            when {
                // Box extends to the end of the file, so there is nothing after it to walk to.
                size == 0L -> if (type != BOX_MOOV) return null else size = fileSize - offset
                size == 1L -> {
                    // 64 bit largesize follows the type.
                    if (offset + 16 > fileSize || !readFully(channel, header, 8)) {
                        return null
                    }
                    size = header.getLong(0)
                    headerSize = 16L
                    if (size < 16) {
                        return null
                    }
                }
                size < 8 -> return null
            }
            if (offset + size > fileSize) {
                return null
            }
            if (type == BOX_MOOV) {
                return readMvhdCreationTime(channel, offset + headerSize, offset + size)
            }
            offset += size
        }
    }

    /** Scans the direct children of `moov` for `mvhd`, which is normally the very first one. */
    private fun readMvhdCreationTime(
        channel: SeekableByteChannel,
        moovContentStart: Long,
        moovEnd: Long
    ): Long? {
        val header = ByteBuffer.allocate(16)
        val scanEnd = minOf(moovEnd, moovContentStart + MAX_MOOV_SCAN_BYTES)
        var offset = moovContentStart
        var boxCount = 0
        while (offset + 8 <= scanEnd) {
            if (++boxCount > MAX_BOX_COUNT) {
                return null
            }
            channel.position(offset)
            if (!readFully(channel, header, 8)) {
                return null
            }
            val size = header.getInt(0).toLong() and 0xFFFFFFFFL
            val type = header.getInt(4)
            if (size < 8 || offset + size > moovEnd) {
                return null
            }
            if (type == BOX_MVHD) {
                return readMvhdBody(channel, offset + 8)
            }
            offset += size
        }
        return null
    }

    private fun readMvhdBody(channel: SeekableByteChannel, bodyStart: Long): Long? {
        val body = ByteBuffer.allocate(12)
        channel.position(bodyStart)
        if (!readFully(channel, body, 4)) {
            return null
        }
        val version = body.get(0).toInt() and 0xFF
        val creationTimeSeconds = when (version) {
            0 -> {
                if (!readFully(channel, body, 4)) {
                    return null
                }
                body.getInt(0).toLong() and 0xFFFFFFFFL
            }
            1 -> {
                if (!readFully(channel, body, 8)) {
                    return null
                }
                body.getLong(0)
            }
            else -> return null
        }
        if (creationTimeSeconds == 0L) {
            // Explicitly "unset" per spec, and also what a stripped file looks like.
            return null
        }
        val epochSeconds = creationTimeSeconds - EPOCH_OFFSET_SECONDS
        val millis = try {
            Instant.ofEpochSecond(epochSeconds).toEpochMilli()
        } catch (e: ArithmeticException) {
            return null
        }
        return if (millis in MIN_VALID_MILLIS..MAX_VALID_MILLIS) millis else null
    }

    /** Reads exactly [count] bytes into [buffer] starting at its position 0. */
    private fun readFully(channel: SeekableByteChannel, buffer: ByteBuffer, count: Int): Boolean {
        buffer.clear()
        buffer.limit(count)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                return false
            }
        }
        return true
    }
}

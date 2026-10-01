/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaCreatedTimeCacheTest {
    @Test
    fun roundTripPreservesValuesAndMissingMetadata() {
        val snapshot = MediaCreatedTimeCacheSnapshot(
            "folder",
            linkedMapOf(
                "사진.jpg" to MediaCreatedTimeCacheEntry("사진.jpg", 123, 456, 321),
                "no-metadata.png" to MediaCreatedTimeCacheEntry(
                    "no-metadata.png", 789, 999, null
                )
            )
        )

        val decoded = MediaCreatedTimeCache.decode(
            snapshot.folderKey, MediaCreatedTimeCache.encode(snapshot)
        )

        assertEquals(snapshot, decoded)
    }

    @Test
    fun corruptedPayloadIsRejected() {
        val snapshot = MediaCreatedTimeCacheSnapshot(
            "folder",
            mapOf("video.mp4" to MediaCreatedTimeCacheEntry("video.mp4", 10, 20, 5))
        )
        val bytes = MediaCreatedTimeCache.encode(snapshot)
        bytes[bytes.lastIndex - 5] = (bytes[bytes.lastIndex - 5].toInt() xor 1).toByte()

        assertThrows(IllegalArgumentException::class.java) {
            MediaCreatedTimeCache.decode(snapshot.folderKey, bytes)
        }
    }

    @Test
    fun trailingBytesAreRejected() {
        val snapshot = MediaCreatedTimeCacheSnapshot("folder", emptyMap())
        val bytes = MediaCreatedTimeCache.encode(snapshot) + byteArrayOf(0)

        assertThrows(IllegalArgumentException::class.java) {
            MediaCreatedTimeCache.decode(snapshot.folderKey, bytes)
        }
    }

    @Test
    fun payloadSizeLimitIncludesHeaderAndChecksum() {
        MediaCreatedTimeCache.requirePayloadSizeWithinLimit(
            MediaCreatedTimeCache.MAX_PAYLOAD_SIZE
        )

        assertThrows(IllegalArgumentException::class.java) {
            MediaCreatedTimeCache.requirePayloadSizeWithinLimit(
                MediaCreatedTimeCache.MAX_PAYLOAD_SIZE + 1
            )
        }
        assertEquals(
            MediaCreatedTimeCache.MAX_FILE_SIZE,
            MediaCreatedTimeCache.MAX_PAYLOAD_SIZE + MediaCreatedTimeCache.FILE_OVERHEAD_SIZE
        )
    }
}

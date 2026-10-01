/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCreatedTimeRepositoryTest {
    @Test
    fun unchangedLookupDoesNotInvalidatePendingWrite() {
        val tracker = MediaCreatedTimeRepository.SnapshotVersionTracker()
        val pendingVersion = tracker.next("folder")

        // An unchanged lookup observes the current version but does not advance it.
        tracker.current("folder")

        assertTrue(tracker.isCurrent("folder", pendingVersion))
    }

    @Test
    fun newerChangedSnapshotInvalidatesOlderPendingWrite() {
        val tracker = MediaCreatedTimeRepository.SnapshotVersionTracker()
        val olderVersion = tracker.next("folder")
        val newerVersion = tracker.next("folder")

        assertFalse(tracker.isCurrent("folder", olderVersion))
        assertTrue(tracker.isCurrent("folder", newerVersion))
    }

    @Test
    fun versionsAreIndependentPerFolder() {
        val tracker = MediaCreatedTimeRepository.SnapshotVersionTracker()
        val firstVersion = tracker.next("first")
        val secondVersion = tracker.next("second")

        tracker.next("first")

        assertFalse(tracker.isCurrent("first", firstVersion))
        assertTrue(tracker.isCurrent("second", secondVersion))
    }
}

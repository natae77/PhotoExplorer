/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import java8.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DirectoryItemCountLoaderTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun requestPublishesCountAndCoalescesDuplicates() = runTest {
        var invocationCount = 0
        val key = key("folder")
        val loader = DirectoryItemCountLoader(this, StandardTestDispatcher(testScheduler)) {
            ++invocationCount
            7
        }
        loader.setCandidates(setOf(key.path))
        loader.setEnabled(true)

        loader.request(key)
        loader.request(key)
        advanceUntilIdle()

        assertEquals(1, invocationCount)
        assertEquals(DirectoryItemCountState.Available(7), loader.get(key))
    }

    @Test
    fun failureDoesNotExposePartialCount() = runTest {
        val key = key("broken")
        val loader = DirectoryItemCountLoader(this, StandardTestDispatcher(testScheduler)) {
            throw IOException("broken stream")
        }
        loader.setCandidates(setOf(key.path))
        loader.setEnabled(true)

        loader.request(key)
        advanceUntilIdle()

        assertEquals(DirectoryItemCountState.Unavailable, loader.get(key))
    }

    @Test
    fun cancellationRestoresNotRequested() = runTest {
        val key = key("slow")
        val loader = DirectoryItemCountLoader(this, StandardTestDispatcher(testScheduler)) {
            awaitCancellation()
        }
        loader.setCandidates(setOf(key.path))
        loader.setEnabled(true)
        loader.request(key)
        runCurrent()

        loader.setEnabled(false)
        advanceUntilIdle()

        assertEquals(DirectoryItemCountState.NotRequested, loader.get(key))
        assertEquals(0, loader.runningCount)
    }

    @Test
    fun staleCompletionAfterGenerationChangeIsDiscarded() = runTest {
        val gate = CompletableDeferred<Int>()
        val key = key("stale")
        val loader = DirectoryItemCountLoader(this, StandardTestDispatcher(testScheduler)) {
            withContext(NonCancellable) { gate.await() }
        }
        loader.setCandidates(setOf(key.path))
        loader.setEnabled(true)
        loader.request(key)
        runCurrent()

        loader.advanceGeneration()
        gate.complete(9)
        advanceUntilIdle()

        assertEquals(DirectoryItemCountState.NotRequested, loader.get(key))
        assertEquals(0, loader.completedCount)
    }

    @Test
    fun runningAndPendingRequestsAreBounded() = runTest {
        val gates = mutableMapOf<Path, CompletableDeferred<Int>>()
        val keys = (0 until 72).map { key("folder-$it") }
        val loader = DirectoryItemCountLoader(
            this, StandardTestDispatcher(testScheduler)
        ) { path ->
            gates.getOrPut(path) { CompletableDeferred() }.await()
        }
        loader.setCandidates(keys.mapTo(mutableSetOf()) { it.path })
        loader.setEnabled(true)

        keys.forEach(loader::request)
        runCurrent()

        assertEquals(DirectoryItemCountLoader.MAX_RUNNING_COUNT, loader.runningCount)
        assertEquals(DirectoryItemCountLoader.MAX_PENDING_COUNT, loader.pendingCount)
        assertTrue(loader.get(keys[4]) is DirectoryItemCountState.NotRequested)

        loader.setEnabled(false)
        gates.values.forEach { it.cancel() }
        advanceUntilIdle()
    }

    @Test
    fun completedCacheUsesLruBound() = runTest {
        val keys = (0 until 260).map { key("cached-$it") }
        val loader = DirectoryItemCountLoader(
            this, StandardTestDispatcher(testScheduler)
        ) { 1 }
        loader.setCandidates(keys.mapTo(mutableSetOf()) { it.path })
        loader.setEnabled(true)

        for (key in keys) {
            loader.request(key)
            advanceUntilIdle()
        }

        assertEquals(DirectoryItemCountLoader.MAX_COMPLETED_COUNT, loader.completedCount)
        assertEquals(DirectoryItemCountState.NotRequested, loader.get(keys.first()))
        assertEquals(DirectoryItemCountState.Available(1), loader.get(keys.last()))
    }

    @Test
    fun rapidCompletionsDeliverEveryUpdate() = runTest {
        val keys = (0 until 12).map { key("update-$it") }
        val loader = DirectoryItemCountLoader(
            this, StandardTestDispatcher(testScheduler)
        ) { 1 }
        val updates = mutableListOf<DirectoryItemCountUpdate>()
        val observer = Observer<DirectoryItemCountUpdate> { updates += it }
        loader.updates.observeForever(observer)
        try {
            loader.setCandidates(keys.mapTo(mutableSetOf()) { it.path })
            loader.setEnabled(true)
            keys.forEach(loader::request)
            advanceUntilIdle()

            assertEquals(keys.map { it.path }.toSet(), updates.map { it.path }.toSet())
            assertEquals(keys.size, updates.size)
        } finally {
            loader.updates.removeObserver(observer)
        }
    }

    private fun key(name: String): DirectoryItemCountKey {
        val path = mock(Path::class.java)
        return DirectoryItemCountKey(path, name.hashCode().toLong())
    }

}

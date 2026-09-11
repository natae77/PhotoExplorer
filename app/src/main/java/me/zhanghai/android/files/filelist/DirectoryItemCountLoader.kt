/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import me.zhanghai.android.files.provider.common.newDirectoryStream
import java.io.IOException
import java.util.ArrayDeque
import java.util.LinkedHashMap

sealed interface DirectoryItemCountState {
    data object NotRequested : DirectoryItemCountState
    data object Loading : DirectoryItemCountState
    data class Available(val count: Int) : DirectoryItemCountState
    data object Unavailable : DirectoryItemCountState
}

data class DirectoryItemCountKey(val path: Path, val lastModifiedMillis: Long)

data class DirectoryItemCountUpdate(
    val path: Path,
    val key: DirectoryItemCountKey,
    val state: DirectoryItemCountState
)

/**
 * Counts direct children without retaining an Adapter, Fragment, View, or ViewHolder.
 *
 * All mutable state is confined to the scope's dispatcher (the ViewModel main dispatcher in
 * production). Only the blocking directory iteration runs on [ioDispatcher].
 */
class DirectoryItemCountLoader(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val counter: suspend (Path) -> Int = { path -> countDirectChildren(path, ioDispatcher) }
) {
    private val _updates = MutableLiveData<DirectoryItemCountUpdate>()
    val updates: LiveData<DirectoryItemCountUpdate> = _updates

    private val completed = LinkedHashMap<Path, CacheEntry>(16, 0.75f, true)
    private val pending = ArrayDeque<Request>()
    private val running = mutableMapOf<Long, RunningRequest>()
    private val loadingByPath = mutableMapOf<Path, Request>()

    private var enabled = false
    private var candidates = emptySet<Path>()
    private var generation = 0L
    private var nextRequestId = 0L

    internal val completedCount: Int
        get() = completed.size
    internal val pendingCount: Int
        get() = pending.size
    internal val runningCount: Int
        get() = running.size

    fun get(key: DirectoryItemCountKey): DirectoryItemCountState {
        val cacheEntry = completed[key.path]
        if (cacheEntry != null) {
            if (cacheEntry.key == key) {
                return cacheEntry.state
            }
            completed.remove(key.path)
        }
        val loading = loadingByPath[key.path]
        return if (loading?.key == key && loading.generation == generation) {
            DirectoryItemCountState.Loading
        } else {
            DirectoryItemCountState.NotRequested
        }
    }

    fun request(key: DirectoryItemCountKey) {
        if (!enabled || key.path !in candidates) {
            return
        }
        if (get(key) !is DirectoryItemCountState.NotRequested) {
            return
        }
        val request = Request(key, generation, ++nextRequestId)
        loadingByPath[key.path] = request
        pending.addLast(request)
        if (pending.size > MAX_PENDING_COUNT) {
            val dropped = pending.removeFirst()
            removeLoadingIfCurrent(dropped)
        }
        drainQueue()
    }

    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) {
            return
        }
        this.enabled = enabled
        if (enabled) {
            drainQueue()
        } else {
            cancelRequests { true }
        }
    }

    fun setCandidates(paths: Set<Path>) {
        candidates = paths
        cancelRequests { it.key.path !in paths }
    }

    fun advanceGeneration() {
        ++generation
        cancelRequests { true }
    }

    fun invalidate(paths: Set<Path>, advanceGeneration: Boolean) {
        if (advanceGeneration) {
            ++generation
        }
        for (path in paths) {
            completed.remove(path)
        }
        cancelRequests { it.key.path in paths || it.generation != generation }
    }

    fun clear() {
        enabled = false
        candidates = emptySet()
        completed.clear()
        ++generation
        cancelRequests { true }
    }

    private fun cancelRequests(predicate: (Request) -> Boolean) {
        val pendingIterator = pending.iterator()
        while (pendingIterator.hasNext()) {
            val request = pendingIterator.next()
            if (predicate(request)) {
                pendingIterator.remove()
                removeLoadingIfCurrent(request)
            }
        }
        for (runningRequest in running.values.toList()) {
            if (predicate(runningRequest.request)) {
                runningRequest.job.cancel()
                removeLoadingIfCurrent(runningRequest.request)
            }
        }
    }

    private fun drainQueue() {
        if (!enabled) {
            return
        }
        while (running.size < MAX_RUNNING_COUNT && pending.isNotEmpty()) {
            val request = pending.removeFirst()
            if (request.generation != generation || request.key.path !in candidates
                || loadingByPath[request.key.path] != request) {
                removeLoadingIfCurrent(request)
                continue
            }
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val state = try {
                    DirectoryItemCountState.Available(counter(request.key.path))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: IOException) {
                    exception.printStackTrace()
                    DirectoryItemCountState.Unavailable
                } catch (exception: SecurityException) {
                    exception.printStackTrace()
                    DirectoryItemCountState.Unavailable
                } catch (exception: DirectoryIteratorException) {
                    exception.printStackTrace()
                    DirectoryItemCountState.Unavailable
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    DirectoryItemCountState.Unavailable
                }
                if (isCurrent(request)) {
                    putCompleted(request.key, state)
                    loadingByPath.remove(request.key.path)
                    _updates.value = DirectoryItemCountUpdate(request.key.path, request.key, state)
                }
            }
            running[request.id] = RunningRequest(request, job)
            job.invokeOnCompletion {
                scope.launch {
                    running.remove(request.id)
                    removeLoadingIfCurrent(request)
                    drainQueue()
                }
            }
            job.start()
        }
    }

    private fun isCurrent(request: Request): Boolean =
        enabled && request.generation == generation && request.key.path in candidates
            && loadingByPath[request.key.path] == request

    private fun removeLoadingIfCurrent(request: Request) {
        if (loadingByPath[request.key.path] == request) {
            loadingByPath.remove(request.key.path)
        }
    }

    private fun putCompleted(key: DirectoryItemCountKey, state: DirectoryItemCountState) {
        completed[key.path] = CacheEntry(key, state)
        while (completed.size > MAX_COMPLETED_COUNT) {
            completed.entries.iterator().run {
                next()
                remove()
            }
        }
    }

    private data class CacheEntry(
        val key: DirectoryItemCountKey,
        val state: DirectoryItemCountState
    )

    private data class Request(
        val key: DirectoryItemCountKey,
        val generation: Long,
        val id: Long
    )

    private data class RunningRequest(val request: Request, val job: Job)

    companion object {
        const val MAX_COMPLETED_COUNT = 256
        const val MAX_PENDING_COUNT = 64
        const val MAX_RUNNING_COUNT = 4
    }
}

private suspend fun countDirectChildren(path: Path, dispatcher: CoroutineDispatcher): Int =
    runInterruptible(dispatcher) {
        var count = 0L
        path.newDirectoryStream().use { directoryStream ->
            for (ignored in directoryStream) {
                if (count == Int.MAX_VALUE.toLong()) {
                    throw ArithmeticException("Directory item count exceeds Int.MAX_VALUE")
                }
                ++count
            }
        }
        count.toInt()
    }

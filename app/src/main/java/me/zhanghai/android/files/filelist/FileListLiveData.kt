/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.AsyncTask
import java8.nio.file.DirectoryIteratorException
import java8.nio.file.Path
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MediaCreatedTimeRepository
import me.zhanghai.android.files.file.loadFileItem
import me.zhanghai.android.files.provider.common.newDirectoryStream
import me.zhanghai.android.files.util.CloseableLiveData
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.util.valueCompat
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

class FileListLiveData(
    private val path: Path,
    val includesMediaCreatedTime: Boolean = false
) : CloseableLiveData<Stateful<List<FileItem>>>() {
    private var future: Future<Unit>? = null
    private val generation = AtomicLong()

    private val observer: PathObserver

    @Volatile
    private var isChangedWhileInactive = false

    init {
        loadValue()
        observer = PathObserver(path) { onChangeObserved() }
    }

    fun loadValue() {
        future?.cancel(true)
        val generation = generation.incrementAndGet()
        value = Loading(value?.value)
        future = (AsyncTask.THREAD_POOL_EXECUTOR as ExecutorService).submit<Unit> {
            val value = try {
                val files = path.newDirectoryStream().use { directoryStream ->
                    val fileList = mutableListOf<FileItem>()
                    for (path in directoryStream) {
                        try {
                            fileList.add(path.loadFileItem())
                        } catch (e: DirectoryIteratorException) {
                            // TODO: Ignoring such a file can be misleading and we need to support
                            //  files without information.
                            e.printStackTrace()
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }
                    fileList as List<FileItem>
                }
                if (includesMediaCreatedTime) {
                    val enrichment = MediaCreatedTimeRepository.enrich(path, files) {
                        isCurrent(generation)
                    }
                    // A complete snapshot remains useful even if this UI request is superseded
                    // immediately after enrichment. A newer changed snapshot gets a higher version
                    // and prevents this one from overwriting it.
                    MediaCreatedTimeRepository.persist(enrichment)
                    Success(enrichment.files)
                } else {
                    Success(files)
                }
            } catch (e: Exception) {
                if (!isCurrent(generation)) {
                    return@submit
                }
                Failure(valueCompat.value, e)
            }
            if (isCurrent(generation)) {
                postValue(value)
            }
        }
    }

    private fun isCurrent(generation: Long): Boolean =
        this.generation.get() == generation && !Thread.currentThread().isInterrupted

    private fun onChangeObserved() {
        if (hasActiveObservers()) {
            loadValue()
        } else {
            isChangedWhileInactive = true
        }
    }

    override fun onActive() {
        if (isChangedWhileInactive) {
            loadValue()
            isChangedWhileInactive = false
        }
    }

    override fun close() {
        observer.close()
        generation.incrementAndGet()
        future?.cancel(true)
    }
}

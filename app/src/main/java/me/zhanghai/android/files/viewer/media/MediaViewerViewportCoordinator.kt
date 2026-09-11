/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import java.lang.ref.WeakReference
import java.util.UUID
import java8.nio.file.Path

/**
 * Connects one internal media viewer to the file list immediately underneath it.
 *
 * Activity results arrive only when the viewer is already finishing, which is too late for the
 * folder to be in the right place when a downward drag first reveals it. This process-local
 * coordinator keeps only the latest request for each explicitly-created session. Sequence numbers
 * make late pre-draw callbacks harmless when pages are flung through quickly.
 */
object MediaViewerViewportCoordinator {
    enum class Preparation {
        PENDING,
        READY,
        UNAVAILABLE
    }

    data class Request(
        val sessionId: String,
        val sequence: Long,
        val path: Path
    )

    data class Status(
        val request: Request,
        val preparation: Preparation
    )

    fun interface FileListListener {
        fun onViewportRequested(request: Request)
    }

    fun interface ViewerListener {
        fun onViewportStatusChanged(status: Status)
    }

    private class Session {
        var nextSequence = 0L
        var request: Request? = null
        var status: Status? = null
        var fileListListener: WeakReference<FileListListener>? = null
        var viewerListener: WeakReference<ViewerListener>? = null
    }

    private val sessions = mutableMapOf<String, Session>()

    fun createSession(): String = UUID.randomUUID().toString().also { sessions[it] = Session() }

    fun registerFileList(sessionId: String, listener: FileListListener) {
        val session = sessions.getOrPut(sessionId, ::Session)
        session.fileListListener = WeakReference(listener)
        session.request?.let(listener::onViewportRequested)
    }

    fun unregisterFileList(sessionId: String, listener: FileListListener) {
        val session = sessions[sessionId] ?: return
        if (session.fileListListener?.get() === listener) {
            session.fileListListener = null
            session.request?.let {
                val status = Status(it, Preparation.PENDING)
                session.status = status
                session.viewerListener?.get()?.onViewportStatusChanged(status)
            }
        }
    }

    fun registerViewer(sessionId: String, listener: ViewerListener) {
        val session = sessions.getOrPut(sessionId, ::Session)
        session.viewerListener = WeakReference(listener)
        session.status?.let(listener::onViewportStatusChanged)
    }

    fun unregisterViewer(sessionId: String, listener: ViewerListener) {
        val session = sessions[sessionId] ?: return
        if (session.viewerListener?.get() === listener) {
            session.viewerListener = null
        }
    }

    fun request(sessionId: String, path: Path): Request {
        val session = sessions.getOrPut(sessionId, ::Session)
        val request = Request(sessionId, ++session.nextSequence, path)
        session.request = request
        val status = Status(request, Preparation.PENDING)
        session.status = status
        session.viewerListener?.get()?.onViewportStatusChanged(status)
        session.fileListListener?.get()?.onViewportRequested(request)
        logMediaTransition("viewport request: session=$sessionId sequence=${request.sequence} path=$path")
        return request
    }

    fun update(request: Request, preparation: Preparation) {
        val session = sessions[request.sessionId] ?: return
        if (session.request != request) {
            logMediaTransition(
                "viewport stale: session=${request.sessionId} sequence=${request.sequence} " +
                    "path=${request.path}"
            )
            return
        }
        val status = Status(request, preparation)
        session.status = status
        session.viewerListener?.get()?.onViewportStatusChanged(status)
        logMediaTransition(
            "viewport status: session=${request.sessionId} sequence=${request.sequence} " +
                "path=${request.path} status=$preparation"
        )
    }

    fun isLatest(request: Request): Boolean = sessions[request.sessionId]?.request == request

    /** Replays the latest request after the file list data or layout has changed. */
    fun invalidateFileList(sessionId: String) {
        val session = sessions[sessionId] ?: return
        val path = session.request?.path ?: return
        val request = Request(sessionId, ++session.nextSequence, path)
        session.request = request
        val status = Status(request, Preparation.PENDING)
        session.status = status
        session.viewerListener?.get()?.onViewportStatusChanged(status)
        session.fileListListener?.get()?.onViewportRequested(request)
    }

    fun endSession(sessionId: String) {
        sessions.remove(sessionId)
    }
}

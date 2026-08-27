/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.content.res.ColorStateList
import android.text.TextUtils
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import java8.nio.file.Path
import me.zhanghai.android.fastscroll.PopupTextProvider
import me.zhanghai.android.files.R
import me.zhanghai.android.files.coil.AppIconPackageName
import me.zhanghai.android.files.compat.foregroundCompat
import me.zhanghai.android.files.compat.getColorCompat
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.isSingleLineCompat
import me.zhanghai.android.files.databinding.FileItemGridBinding
import me.zhanghai.android.files.databinding.FileItemListBinding
import me.zhanghai.android.files.databinding.FileItemMediaBinding
import me.zhanghai.android.files.databinding.FileItemMediaDateBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.fileSize
import me.zhanghai.android.files.file.formatShort
import me.zhanghai.android.files.file.iconRes
import me.zhanghai.android.files.file.isApk
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.provider.archive.isArchivePath
import me.zhanghai.android.files.provider.common.isEncrypted
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.AnimatedListAdapter
import me.zhanghai.android.files.ui.CheckableForegroundLinearLayout
import me.zhanghai.android.files.ui.CheckableItemBackground
import me.zhanghai.android.files.util.isMaterial3Theme
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.valueCompat
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class FileListAdapter(
    private val listener: Listener
) : AnimatedListAdapter<FileListItem, RecyclerView.ViewHolder>(CALLBACK), PopupTextProvider {
    private var isSearching = false

    // The list the adapter displays is rebuilt from this every time, never from the list currently
    // on screen. Sorting or re-inserting date tiles on top of an already built list would duplicate
    // them, and switching away from media mode would leave them behind. See plan 4.1.
    private var files: List<FileItem> = emptyList()

    private lateinit var _viewType: FileViewType
    var viewType: FileViewType
        get() = _viewType
        set(value) {
            _viewType = value
            if (!isSearching) {
                rebuildItems(true)
            }
        }

    private lateinit var _sortOptions: FileSortOptions
    var sortOptions: FileSortOptions
        get() = _sortOptions
        set(value) {
            _sortOptions = value
            if (!isSearching) {
                rebuildItems(true)
            }
        }

    var pickOptions: PickOptions? = null
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    private val selectedFiles = fileItemSetOf()

    private val filePositionMap = mutableMapOf<Path, Int>()

    private lateinit var _nameEllipsize: TextUtils.TruncateAt
    var nameEllipsize: TextUtils.TruncateAt
        get() = _nameEllipsize
        set(value) {
            _nameEllipsize = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE_CHANGED)
        }

    fun replaceSelectedFiles(files: FileItemSet) {
        val changedFiles = fileItemSetOf()
        val iterator = selectedFiles.iterator()
        while (iterator.hasNext()) {
            val file = iterator.next()
            if (file !in files) {
                iterator.remove()
                changedFiles.add(file)
            }
        }
        for (file in files) {
            if (file !in selectedFiles) {
                selectedFiles.add(file)
                changedFiles.add(file)
            }
        }
        for (file in changedFiles) {
            val position = filePositionMap[file.path]
            position?.let { notifyItemChanged(it, PAYLOAD_STATE_CHANGED) }
        }
    }

    private fun selectFile(file: FileItem) {
        if (!isFileSelectable(file)) {
            return
        }
        val selected = file in selectedFiles
        val pickOptions = pickOptions
        if (!selected && pickOptions != null && !pickOptions.allowMultiple) {
            listener.clearSelectedFiles()
        }
        listener.selectFile(file, !selected)
    }

    fun selectAllFiles() {
        val files = fileItemSetOf()
        for (index in 0..<itemCount) {
            val item = getItem(index) as? FileListItem.File ?: continue
            val file = item.file
            if (isFileSelectable(file)) {
                files.add(file)
            }
        }
        listener.selectFiles(files, true)
    }

    private fun isFileSelectable(file: FileItem): Boolean {
        val pickOptions = pickOptions ?: return true
        return when (pickOptions.mode) {
            PickOptions.Mode.OPEN_FILE, PickOptions.Mode.CREATE_FILE ->
                !file.attributes.isDirectory &&
                    pickOptions.mimeTypes.any { it.match(file.mimeType) }
            PickOptions.Mode.OPEN_DIRECTORY -> file.attributes.isDirectory
        }
    }

    override fun clear() {
        files = emptyList()
        super.clear()

        rebuildFilePositionMap()
    }

    @Deprecated("", ReplaceWith("replaceListAndSearching(list, searching)"))
    override fun replace(list: List<FileListItem>, clear: Boolean) {
        throw UnsupportedOperationException()
    }

    fun replaceListAndIsSearching(list: List<FileItem>, isSearching: Boolean) {
        val clear = this.isSearching != isSearching
        this.isSearching = isSearching
        files = list
        rebuildItems(clear)
    }

    private fun rebuildItems(clear: Boolean) {
        // The view type and sort options arrive from two separate LiveData observers, and whichever
        // fires first would otherwise read the other one before it is set.
        if (!this::_viewType.isInitialized || !this::_sortOptions.isInitialized) {
            return
        }
        val items = if (isSearching) {
            // Search results are neither sorted nor grouped by date, see spec 4.3.
            files.map { FileListItem.File(it) }
        } else {
            buildItems(files)
        }
        super.replace(items, clear)
        rebuildFilePositionMap()
    }

    /**
     * Media mode pins the sort criterion to the media created time (spec 8), but does *not* store
     * that choice: the sort options are persisted per folder, so writing it would leave the folder
     * stuck on MEDIA_CREATED after switching back to list or grid. Only the direction and
     * directories-first come from what the user actually picked.
     */
    val effectiveSortOptions: FileSortOptions
        get() = if (viewType == FileViewType.MEDIA) {
            sortOptions.copy(by = FileSortOptions.By.MEDIA_CREATED)
        } else {
            sortOptions
        }

    private fun buildItems(files: List<FileItem>): List<FileListItem> {
        val sortOptions = effectiveSortOptions
        val sorted = files.sortedWith(sortOptions.createComparator())
        if (viewType != FileViewType.MEDIA) {
            return sorted.map { FileListItem.File(it) }
        }
        // Walking the already sorted list and inserting where the day changes is what makes the
        // date tile land first within its day for both sort orders, with no extra rule. See
        // spec 4.3.
        val zone = ZoneId.systemDefault()
        val items = mutableListOf<FileListItem>()
        var lastDate: LocalDate? = null
        for (file in sorted) {
            if (sortOptions.isDirectoriesFirst && file.attributes.isDirectory) {
                // The leading run of directories gets no date tiles: the first one belongs right
                // after the last directory, not before it.
                items.add(FileListItem.File(file))
                continue
            }
            val date = Instant.ofEpochMilli(file.mediaCreatedTimeMillisOrLastModified)
                .atZone(zone)
                .toLocalDate()
            if (date != lastDate) {
                items.add(
                    FileListItem.Date(date.atStartOfDay(zone).toInstant().toEpochMilli())
                )
                lastDate = date
            }
            items.add(FileListItem.File(file))
        }
        return items
    }

    private fun rebuildFilePositionMap() {
        filePositionMap.clear()
        for (index in 0..<itemCount) {
            // Date items are not in the map, but the positions stored here are still full adapter
            // positions, because that is what notifyItemChanged() takes.
            val item = getItem(index) as? FileListItem.File ?: continue
            filePositionMap[item.file.path] = index
        }
    }

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is FileListItem.File -> viewType.ordinal
            // onCreateViewHolder() maps view types back with FileViewType.entries[viewType], so the
            // date view type has to sit past the end of that array.
            is FileListItem.Date -> VIEW_TYPE_DATE
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = parent.context.layoutInflater
        if (viewType == VIEW_TYPE_DATE) {
            return DateViewHolder(FileItemMediaDateBinding.inflate(inflater, parent, false))
        }
        val fileViewType = FileViewType.entries[viewType]
        val holder = when (fileViewType) {
            FileViewType.LIST -> ViewHolder(FileItemListBinding.inflate(inflater, parent, false))
            FileViewType.GRID -> ViewHolder(FileItemGridBinding.inflate(inflater, parent, false))
            FileViewType.MEDIA -> ViewHolder(FileItemMediaBinding.inflate(inflater, parent, false))
        }
        return holder.apply {
            itemLayout.apply {
                val context = context
                val isMaterial3Theme = context.isMaterial3Theme
                if (fileViewType == FileViewType.GRID && isMaterial3Theme) {
                    foregroundCompat =
                        context.getDrawableCompat(R.drawable.file_item_grid_foreground_material3)
                }
                background = if (fileViewType == FileViewType.GRID && isMaterial3Theme) {
                    CheckableItemBackground.create(4f, 12f, context)
                } else {
                    CheckableItemBackground.create(0f, 0f, context)
                }
            }
            thumbnailOutlineView?.apply {
                val context = context
                if (context.isMaterial3Theme) {
                    background = context.getDrawableCompat(
                        R.drawable.file_item_grid_thumbnail_outline_material3
                    )
                }
            }
            popupMenu = PopupMenu(menuButton.context, menuButton)
                .apply { inflate(R.menu.file_item) }
            menuButton.setOnClickListener { popupMenu.show() }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        when (val item = getItem(position)) {
            is FileListItem.Date -> bindDateViewHolder(holder as DateViewHolder, item, payloads)
            is FileListItem.File ->
                bindFileViewHolder(holder as ViewHolder, item.file, payloads)
        }
    }

    private fun bindDateViewHolder(
        holder: DateViewHolder,
        item: FileListItem.Date,
        payloads: List<Any>
    ) {
        // Date tiles have no state that PAYLOAD_STATE_CHANGED could affect. Returning early also
        // keeps pickOptions/nameEllipsize changes from rebinding them as if they were files.
        if (payloads.isNotEmpty()) {
            return
        }
        bindViewHolderAnimation(holder)
        val date = Date(item.epochMillis)
        holder.yearText.text = formatDate(date, "y")
        holder.dateText.text = formatDate(date, "MMMd")
        // Weekend colouring on the day line only, see spec 4.3 (D23). Same time zone as the one
        // that decided which day this tile belongs to.
        val dayOfWeek = Instant.ofEpochMilli(item.epochMillis)
            .atZone(ZoneId.systemDefault())
            .dayOfWeek
        when (dayOfWeek) {
            DayOfWeek.SATURDAY ->
                holder.dateText.setTextColor(
                    holder.dateText.context.getColorCompat(R.color.media_date_saturday)
                )
            DayOfWeek.SUNDAY ->
                holder.dateText.setTextColor(
                    holder.dateText.context.getColorCompat(R.color.media_date_sunday)
                )
            // Must be put back: this holder may have been a weekend tile a moment ago.
            else -> holder.dateText.setTextColor(holder.defaultDateTextColors)
        }
        // Read as one label instead of two, and never announced as actionable. See spec 4.3.
        holder.itemView.contentDescription = formatDate(date, "yMMMd")
        // No click listeners are attached at all, so a date tile cannot be tapped or long pressed.
    }

    /**
     * Formats [date] with the locale's preferred layout for [skeleton].
     *
     * SimpleDateFormat rather than android.text.format.DateFormat.format(): the latter renders a
     * single "y" in the pattern as a two digit year, so the date tiles would read "26" instead of
     * "2026".
     */
    private fun formatDate(date: Date, skeleton: String): String {
        val locale = Locale.getDefault()
        return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .format(date)
    }

    private fun bindFileViewHolder(holder: ViewHolder, file: FileItem, payloads: List<Any>) {
        val isMedia = viewType == FileViewType.MEDIA
        val isDirectory = file.attributes.isDirectory
        val isEnabled = isFileSelectable(file) || isDirectory
        holder.itemLayout.isEnabled = isEnabled
        holder.menuButton.isEnabled = isEnabled
        val menu = holder.popupMenu.menu
        val path = file.path
        val hasPickOptions = pickOptions != null
        val isReadOnly = path.fileSystem.isReadOnly
        menu.findItem(R.id.action_cut).isVisible = !hasPickOptions && !isReadOnly
        menu.findItem(R.id.action_copy).isVisible = !hasPickOptions
        val checked = file in selectedFiles
        holder.itemLayout.isChecked = checked
        if (isMedia) {
            // Media tiles have no icon area to put the check badge in, so the menu button and the
            // check mark share the same corner. See spec 4.1.
            holder.checkImage?.isVisible = checked
            holder.menuButton.isVisible = !checked
            holder.menuScrimView?.isVisible = !checked
        }
        holder.nameText.apply {
            if (isSingleLineCompat) {
                val nameEllipsize = nameEllipsize
                ellipsize = nameEllipsize
                isSelected = nameEllipsize == TextUtils.TruncateAt.MARQUEE
            }
        }
        if (payloads.isNotEmpty()) {
            return
        }
        bindViewHolderAnimation(holder)
        holder.itemLayout.apply {
            setOnClickListener {
                if (selectedFiles.isEmpty()) {
                    listener.openFile(file)
                } else {
                    selectFile(file)
                }
            }
            setOnLongClickListener {
                if (selectedFiles.isEmpty()) {
                    selectFile(file)
                } else {
                    listener.openFile(file)
                }
                true
            }
        }
        holder.iconLayout?.setOnClickListener { selectFile(file) }
        val iconRes = file.mimeType.iconRes
        holder.iconImage?.apply {
            isVisible = true
            setImageResource(iconRes)
        }
        holder.directoryThumbnailImage?.isVisible = isDirectory
        holder.thumbnailOutlineView?.isVisible = !isDirectory
        val supportsThumbnail = file.supportsThumbnail
        val shouldLoadThumbnailIcon = supportsThumbnail && holder.thumbnailIconImage != null &&
            file.mimeType.isApk
        val attributes = file.attributes
        holder.thumbnailIconImage?.apply {
            dispose()
            isVisible = !isDirectory
            setImageResource(iconRes)
            if (shouldLoadThumbnailIcon) {
                load(path to attributes)
            }
        }
        holder.thumbnailImage.apply {
            dispose()
            setImageDrawable(null)
            val shouldLoadThumbnail = supportsThumbnail && !shouldLoadThumbnailIcon
            isVisible = shouldLoadThumbnail
            if (shouldLoadThumbnail) {
                load(path to attributes) {
                    listener { _, _ ->
                        val iconImage = holder.thumbnailIconImage ?: holder.iconImage
                        iconImage?.isVisible = false
                    }
                }
            }
        }
        holder.appIconBadgeImage?.apply {
            dispose()
            setImageDrawable(null)
            val appDirectoryPackageName = file.appDirectoryPackageName
            val hasAppIconBadge = appDirectoryPackageName != null
            isVisible = hasAppIconBadge
            if (hasAppIconBadge) {
                load(AppIconPackageName(appDirectoryPackageName!!))
            }
        }
        // Badges are omitted in media mode, the information is still in the properties dialog.
        holder.badgeImage?.apply {
            val badgeIconRes = if (file.attributesNoFollowLinks.isSymbolicLink) {
                if (file.isSymbolicLinkBroken) {
                    R.drawable.error_badge_icon_18dp
                } else {
                    R.drawable.symbolic_link_badge_icon_18dp
                }
            } else if (file.attributesNoFollowLinks.isEncrypted()) {
                R.drawable.encrypted_badge_icon_18dp
            } else {
                null
            }
            val hasBadge = badgeIconRes != null
            isVisible = hasBadge
            if (hasBadge) {
                setImageResource(badgeIconRes!!)
            } else {
                setImageDrawable(null)
            }
        }
        holder.videoBadgeImage?.isVisible = !isDirectory && file.mimeType.isVideo
        if (isMedia) {
            // Only folder tiles show a name, media tiles are name-less by design. See spec 3.
            holder.nameText.isVisible = isDirectory
            holder.nameText.text = if (isDirectory) file.name else null
            // The name is not on screen for media tiles, so it has to be in the content
            // description. See spec 10.
            holder.itemLayout.contentDescription = file.name
        } else {
            holder.nameText.text = file.name
        }
        holder.descriptionText?.text = if (isDirectory) {
            null
        } else {
            val context = holder.descriptionText!!.context
            val lastModificationTime = attributes.lastModifiedTime().toInstant()
                .formatShort(context)
            val size = attributes.fileSize.formatHumanReadable(context)
            val descriptionSeparator = context.getString(R.string.file_item_description_separator)
            listOf(lastModificationTime, size).joinToString(descriptionSeparator)
        }
        val isArchivePath = path.isArchivePath
        menu.findItem(R.id.action_copy)
            .setTitle(if (isArchivePath) R.string.file_item_action_extract else R.string.copy)
        menu.findItem(R.id.action_delete).isVisible = !isReadOnly
        menu.findItem(R.id.action_rename).isVisible = !isReadOnly
        menu.findItem(R.id.action_extract).isVisible = file.isArchiveFile
        menu.findItem(R.id.action_archive).isVisible = !isArchivePath
        menu.findItem(R.id.action_add_bookmark).isVisible = isDirectory
        holder.popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.action_open_with -> {
                    listener.openFileWith(file)
                    true
                }
                R.id.action_cut -> {
                    listener.cutFile(file)
                    true
                }
                R.id.action_copy -> {
                    listener.copyFile(file)
                    true
                }
                R.id.action_delete -> {
                    listener.confirmDeleteFile(file)
                    true
                }
                R.id.action_rename -> {
                    listener.showRenameFileDialog(file)
                    true
                }
                R.id.action_extract -> {
                    listener.extractFile(file)
                    true
                }
                R.id.action_archive -> {
                    listener.showCreateArchiveDialog(file)
                    true
                }
                R.id.action_share -> {
                    listener.shareFile(file)
                    true
                }
                R.id.action_copy_path -> {
                    listener.copyPath(file)
                    true
                }
                R.id.action_add_bookmark -> {
                    listener.addBookmark(file)
                    true
                }
                R.id.action_create_shortcut -> {
                    listener.createShortcut(file)
                    true
                }
                R.id.action_properties -> {
                    listener.showPropertiesDialog(file)
                    true
                }
                else -> false
            }
        }
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        val locale = Locale.getDefault()
        // Not a feature, just a value that has to exist so that fast scroll does not crash on a
        // date tile. Showing the date in the popup is a non-goal, see spec 2.
        val item = getItem(position)
        if (item is FileListItem.Date) {
            return formatDate(Date(item.epochMillis), "yMMMd")
        }
        val file = (item as FileListItem.File).file
        return when (effectiveSortOptions.by) {
            FileSortOptions.By.NAME -> file.name.take(1).uppercase(locale)
            FileSortOptions.By.TYPE -> file.extension.uppercase(locale)
            FileSortOptions.By.SIZE -> file.attributes.fileSize.formatHumanReadable(view.context)
            FileSortOptions.By.LAST_MODIFIED ->
                file.attributes.lastModifiedTime().toInstant().formatShort(view.context)
            FileSortOptions.By.MEDIA_CREATED ->
                Instant.ofEpochMilli(file.mediaCreatedTimeMillisOrLastModified)
                    .formatShort(view.context)
        }
    }

    override val isAnimationEnabled: Boolean
        get() = Settings.FILE_LIST_ANIMATION.valueCompat

    companion object {
        private val PAYLOAD_STATE_CHANGED = Any()

        private val VIEW_TYPE_DATE = FileViewType.entries.size

        private val CALLBACK = object : DiffUtil.ItemCallback<FileListItem>() {
            override fun areItemsTheSame(oldItem: FileListItem, newItem: FileListItem): Boolean =
                when {
                    oldItem is FileListItem.File && newItem is FileListItem.File ->
                        oldItem.file.path == newItem.file.path
                    oldItem is FileListItem.Date && newItem is FileListItem.Date ->
                        oldItem.epochMillis == newItem.epochMillis
                    else -> false
                }

            override fun areContentsTheSame(oldItem: FileListItem, newItem: FileListItem): Boolean =
                oldItem == newItem
        }
    }

    class ViewHolder private constructor(
        root: View,
        val itemLayout: CheckableForegroundLinearLayout,
        val iconLayout: View?,
        val iconImage: ImageView?,
        val directoryThumbnailImage: ImageView?,
        val thumbnailOutlineView: View?,
        val thumbnailIconImage: ImageView?,
        val thumbnailImage: ImageView,
        val appIconBadgeImage: ImageView?,
        val badgeImage: ImageView?,
        val nameText: TextView,
        val descriptionText: TextView?,
        val menuButton: ImageButton,
        val videoBadgeImage: ImageView? = null,
        val checkImage: ImageView? = null,
        val menuScrimView: View? = null
    ) : RecyclerView.ViewHolder(root) {
        constructor(binding: FileItemListBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            null,
            null,
            null,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            binding.descriptionText,
            binding.menuButton
        )

        constructor(binding: FileItemGridBinding) : this(
            binding.root,
            binding.itemLayout,
            binding.iconLayout,
            binding.iconImage,
            binding.directoryThumbnailImage,
            binding.thumbnailOutlineView,
            binding.thumbnailIconImage,
            binding.thumbnailImage,
            binding.appIconBadgeImage,
            binding.badgeImage,
            binding.nameText,
            null,
            binding.menuButton
        )

        constructor(binding: FileItemMediaBinding) : this(
            binding.root,
            binding.itemLayout,
            null,
            null,
            binding.directoryThumbnailImage,
            null,
            binding.thumbnailIconImage,
            binding.thumbnailImage,
            null,
            null,
            binding.nameText,
            null,
            binding.menuButton,
            binding.videoBadgeImage,
            binding.checkImage,
            binding.menuScrimView
        )

        lateinit var popupMenu: PopupMenu
    }

    class DateViewHolder(
        binding: FileItemMediaDateBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        val yearText: TextView = binding.yearText
        val dateText: TextView = binding.dateText

        /**
         * The colour the theme gave [dateText], captured before anything recolours it.
         *
         * View holders are recycled, so a Saturday tile that was painted blue comes back as some
         * Wednesday and has to be put back. A hardcoded constant would not follow the light/dark
         * theme, and `currentTextColor` would only keep the colour for the current state, so the
         * whole [ColorStateList] is kept.
         */
        val defaultDateTextColors: ColorStateList = dateText.textColors
    }

    interface Listener {
        fun clearSelectedFiles()
        fun selectFile(file: FileItem, selected: Boolean)
        fun selectFiles(files: FileItemSet, selected: Boolean)
        fun openFile(file: FileItem)
        fun openFileWith(file: FileItem)
        fun cutFile(file: FileItem)
        fun copyFile(file: FileItem)
        fun confirmDeleteFile(file: FileItem)
        fun showRenameFileDialog(file: FileItem)
        fun extractFile(file: FileItem)
        fun showCreateArchiveDialog(file: FileItem)
        fun shareFile(file: FileItem)
        fun copyPath(file: FileItem)
        fun addBookmark(file: FileItem)
        fun createShortcut(file: FileItem)
        fun showPropertiesDialog(file: FileItem)
    }
}

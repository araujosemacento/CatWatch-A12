package com.catwatch.detector.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.databinding.ItemFeedAlbumGridBinding
import com.catwatch.detector.databinding.ItemFeedDateHeaderBinding
import com.catwatch.detector.ui.model.FeedItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CatEventAdapter : ListAdapter<FeedItem, RecyclerView.ViewHolder>(DiffCallback) {

    var isSelectionMode: Boolean = false
        private set

    val selectedSessionIds = mutableSetOf<String>()

    var onAlbumClick: ((FeedItem.SessionHeader) -> Unit)? = null
    var onSelectionChanged: ((Int) -> Unit)? = null

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedSessionIds.clear()
            }
            notifyDataSetChanged()
            onSelectionChanged?.invoke(selectedSessionIds.size)
        }
    }

    fun toggleSelection(sessionId: String, position: Int) {
        if (selectedSessionIds.contains(sessionId)) {
            selectedSessionIds.remove(sessionId)
        } else {
            selectedSessionIds.add(sessionId)
        }
        notifyItemChanged(position)
        onSelectionChanged?.invoke(selectedSessionIds.size)
    }

    fun selectAll() {
        selectedSessionIds.clear()
        currentList.filterIsInstance<FeedItem.SessionHeader>().forEach {
            selectedSessionIds.add(it.sessionId)
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedSessionIds.size)
    }

    fun clearSelection() {
        selectedSessionIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedEvents(): List<CatEventEntity> {
        return currentList.filterIsInstance<FeedItem.SessionHeader>()
            .filter { selectedSessionIds.contains(it.sessionId) }
            .flatMap { it.eventsInSession }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FeedItem.DateHeader -> TYPE_DATE_HEADER
            is FeedItem.SessionHeader -> TYPE_ALBUM_GRID
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_DATE_HEADER) {
            val binding = ItemFeedDateHeaderBinding.inflate(inflater, parent, false)
            DateHeaderViewHolder(binding)
        } else {
            val binding = ItemFeedAlbumGridBinding.inflate(inflater, parent, false)
            AlbumGridViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)

        when (holder) {
            is DateHeaderViewHolder -> holder.bind(item as FeedItem.DateHeader)
            is AlbumGridViewHolder -> holder.bind(item as FeedItem.SessionHeader, position)
        }
    }

    inner class DateHeaderViewHolder(
        private val binding: ItemFeedDateHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FeedItem.DateHeader) {
            binding.dateHeaderTextView.text = item.dateText
        }
    }

    inner class AlbumGridViewHolder(
        private val binding: ItemFeedAlbumGridBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private var loadJob: Job? = null

        fun bind(item: FeedItem.SessionHeader, position: Int) {
            binding.albumTitleTextView.text = "Álbum ${item.hourRange}"
            binding.albumDateTextView.text = item.dateText
            binding.albumCountBadgeTextView.text = "${item.itemCount} fotos"

            if (isSelectionMode) {
                binding.selectCheckBox.visibility = View.VISIBLE
                binding.selectCheckBox.isChecked = selectedSessionIds.contains(item.sessionId)
                binding.selectCheckBox.setOnClickListener {
                    toggleSelection(item.sessionId, position)
                }
            } else {
                binding.selectCheckBox.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item.sessionId, position)
                } else {
                    onAlbumClick?.invoke(item)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    setSelectionMode(true)
                    toggleSelection(item.sessionId, position)
                }
                true
            }

            loadJob?.cancel()
            binding.albumCoverImageView.setImageDrawable(null)
            loadJob = CoroutineScope(Dispatchers.Main).launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadSubsampledBitmap(item.coverPhoto.filePath, 200, 200)
                }
                if (bitmap != null) {
                    binding.albumCoverImageView.setImageBitmap(bitmap)
                } else {
                    binding.albumCoverImageView.setImageResource(android.R.drawable.ic_menu_camera)
                }
            }
        }
    }

    private fun loadSubsampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            var inSampleSize = 1
            if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val TYPE_DATE_HEADER = 0
        const val TYPE_ALBUM_GRID = 1

        val DiffCallback = object : DiffUtil.ItemCallback<FeedItem>() {
            override fun areItemsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: FeedItem, newItem: FeedItem): Boolean {
                return oldItem == newItem
            }
        }
    }
}

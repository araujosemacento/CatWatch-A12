package com.catwatch.detector.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.catwatch.detector.data.CatEventEntity
import com.catwatch.detector.databinding.ItemCatEventBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CatEventAdapter : ListAdapter<CatEventEntity, CatEventAdapter.CatEventViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    var isSelectionMode: Boolean = false
        private set

    val selectedIds = mutableSetOf<Long>()

    var onItemClick: ((CatEventEntity) -> Unit)? = null
    var onItemLongClick: ((CatEventEntity) -> Unit)? = null
    var onSelectionChanged: ((Int) -> Unit)? = null

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedIds.clear()
            }
            notifyDataSetChanged()
            onSelectionChanged?.invoke(selectedIds.size)
        }
    }

    fun toggleSelection(id: Long, position: Int) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyItemChanged(position)
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(currentList.map { it.id })
        notifyDataSetChanged()
        onSelectionChanged?.invoke(selectedIds.size)
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged?.invoke(0)
    }

    fun getSelectedItems(): List<CatEventEntity> {
        return currentList.filter { selectedIds.contains(it.id) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatEventViewHolder {
        val binding = ItemCatEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CatEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CatEventViewHolder, position: Int) {
        val event = getItem(position)
        holder.bind(event, position)

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(event.id, holder.bindingAdapterPosition)
            } else {
                onItemClick?.invoke(event)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                setSelectionMode(true)
                toggleSelection(event.id, holder.bindingAdapterPosition)
            }
            onItemLongClick?.invoke(event)
            true
        }
    }

    inner class CatEventViewHolder(
        private val binding: ItemCatEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var loadJob: Job? = null

        fun bind(event: CatEventEntity, position: Int) {
            binding.timestampTextView.text = dateFormat.format(Date(event.timestamp))
            binding.confidenceTextView.text = "Confiança: %.1f%%".format(event.confidence * 100)
            binding.filePathTextView.text = File(event.filePath).name

            // Status Badge: Hidratação vs Aproximação (sem emojis, com ícones vetoriais nativos)
            if (event.isConfirmedDrinking || event.eventType == "DRINKING") {
                binding.statusBadgeTextView.text = "Bebendo"
                binding.statusBadgeTextView.setBackgroundResource(com.catwatch.detector.R.drawable.bg_badge_drinking)
                binding.statusBadgeTextView.setCompoundDrawablesWithIntrinsicBounds(com.catwatch.detector.R.drawable.ic_water_drop, 0, 0, 0)
            } else {
                binding.statusBadgeTextView.text = "Aproximação"
                binding.statusBadgeTextView.setBackgroundResource(com.catwatch.detector.R.drawable.bg_badge_approach)
                binding.statusBadgeTextView.setCompoundDrawablesWithIntrinsicBounds(com.catwatch.detector.R.drawable.ic_visibility, 0, 0, 0)
            }

            // Controle de Checkbox no Modo de Seleção Múltipla
            if (isSelectionMode) {
                binding.selectCheckBox.visibility = View.VISIBLE
                binding.selectCheckBox.isChecked = selectedIds.contains(event.id)
                binding.selectCheckBox.setOnClickListener {
                    toggleSelection(event.id, position)
                }
            } else {
                binding.selectCheckBox.visibility = View.GONE
            }

            // Carregamento de Thumbnail em RGB_565 para proteção de memória
            loadJob?.cancel()
            binding.thumbnailImageView.setImageDrawable(null)

            loadJob = CoroutineScope(Dispatchers.Main).launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadSubsampledBitmap(event.filePath, 150, 150)
                }
                if (bitmap != null) {
                    binding.thumbnailImageView.setImageBitmap(bitmap)
                } else {
                    binding.thumbnailImageView.setImageResource(android.R.drawable.ic_menu_camera)
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
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CatEventEntity>() {
        override fun areItemsTheSame(oldItem: CatEventEntity, newItem: CatEventEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CatEventEntity, newItem: CatEventEntity): Boolean {
            return oldItem == newItem
        }
    }
}

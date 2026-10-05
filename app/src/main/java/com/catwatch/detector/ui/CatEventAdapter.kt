package com.catwatch.detector.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatEventViewHolder {
        val binding = ItemCatEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CatEventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CatEventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CatEventViewHolder(
        private val binding: ItemCatEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var loadJob: Job? = null

        fun bind(event: CatEventEntity) {
            binding.timestampTextView.text = dateFormat.format(Date(event.timestamp))
            binding.confidenceTextView.text = "Confiança: %.1f%%".format(event.confidence * 100)
            binding.filePathTextView.text = File(event.filePath).name

            loadJob?.cancel()
            binding.thumbnailImageView.setImageDrawable(null)

            // Carregamento assíncrono com subamostragem e RGB_565 para proteção de memória
            loadJob = CoroutineScope(Dispatchers.Main).launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadSubsampledBitmap(event.filePath, 144, 144)
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

package com.catwatch.detector.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object ImageUtils {
    fun loadHighResBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
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
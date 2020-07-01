package info.hannes.cvscanner.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

object Utility {
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int, keepAspectRatio: Boolean): Int { // Raw height and width of image
        var reqHeight = reqHeight
        val height = options.outHeight
        val width = options.outWidth
        val aspectRatio = height.toFloat() / width
        var inSampleSize = 1
        if (keepAspectRatio) {
            reqHeight = Math.round(reqWidth * aspectRatio)
        }
        if (reqHeight > 0 && reqWidth > 0) { // Calculate ratios of height and width to requested height and width
            val heightRatio = Math.round(height.toFloat() / reqHeight.toFloat())
            val widthRatio = Math.round(width.toFloat() / reqWidth.toFloat())
            // Choose the smallest ratio as inSampleSize value, this will guarantee
// a final image with both dimensions larger than or equal to the
// requested height and width.
            inSampleSize = if (heightRatio < widthRatio) heightRatio else widthRatio
        }
        return inSampleSize
    }

    fun getBitmapFromPath(path: String?, width: Int, height: Int): Bitmap? {
        if (path != null) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            val sampleSize = calculateInSampleSize(options, width, height, true)
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            return BitmapFactory.decodeFile(path, options)
        }
        return null
    }

    fun saveBitmapJPG(img: Bitmap, imageName: String?): String? {
        val dir = File(Environment.getExternalStorageDirectory(), "/" + "CVScannerSample" + "/")
        dir.mkdirs()
        val file = File(dir, imageName)
        val fOut: FileOutputStream
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
            fOut = FileOutputStream(file)
            img.compress(Bitmap.CompressFormat.JPEG, 100, fOut)
            fOut.flush()
            fOut.close()
            return file.absolutePath
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    fun deleteFilePermanently(filePath: String?): Boolean {
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                return if (file.delete()) {
                    true
                } else {
                    file.absoluteFile.delete()
                }
            }
        }
        return false
    }
}
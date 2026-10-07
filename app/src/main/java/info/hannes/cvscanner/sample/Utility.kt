package info.hannes.cvscanner.sample

import java.io.File

object Utility {

    fun deleteFilePermanently(filePath: String?): Boolean {
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists()) {
                return file.delete() || file.absoluteFile.delete()
            }
        }
        return false
    }
}
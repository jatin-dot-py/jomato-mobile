package com.application.jomato.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream

fun saveLogsUsingMediaStore(context: Context, logFile: File, fileName: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return null

        resolver.openOutputStream(uri)?.use { outputStream ->
            copyFile(logFile, outputStream)
        }

        return "Downloads/$fileName"
    } catch (e: Exception) {
        FileLogger.log(context, "LogViewer", "MediaStore save error", e)
        return null
    }
}

fun saveLogsToLegacyDownloads(context: Context, logFile: File, fileName: String): String? {
    try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val destFile = File(downloadsDir, fileName)
        logFile.copyTo(destFile, overwrite = true)

        return destFile.absolutePath
    } catch (e: Exception) {
        FileLogger.log(context, "LogViewer", "Legacy save error", e)
        return null
    }
}

fun copyFile(source: File, outputStream: OutputStream) {
    FileInputStream(source).use { input ->
        input.copyTo(outputStream)
    }
}
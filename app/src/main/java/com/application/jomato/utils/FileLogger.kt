package com.application.jomato.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val FILE_NAME = "jomato_daemon.log"

    /** Max size before trimming (2 MB). */
    private const val MAX_LOG_FILE_BYTES = 2L * 1024 * 1024

    /** When trimming, keep this many bytes from the end (1 MB). */
    private const val TRIM_KEEP_BYTES = 1024 * 1024

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val TOKEN_PATTERN = Regex("(?i)(token|key|auth)=([\\w\\-.]+)?")
    private val PHONE_PATTERN = Regex("\\b\\d{10}\\b")
    private val OTP_PATTERN = Regex("\\b\\d{6}\\b")

    @Synchronized
    fun log(context: Context, tag: String, message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(tag, message, error)
        } else {
            Log.i(tag, message)
        }
        try {
            val file = File(context.filesDir, FILE_NAME)
            trimLogFileIfNeeded(file)
            val timestamp = dateFormat.format(Date())
            val cleanMessage = sanitize(message)
            val cleanError = error?.let { "\nStacktrace: ${it.stackTraceToString()}" } ?: ""
            val entry = "$timestamp [$tag] $cleanMessage$cleanError\n"
            FileWriter(file, true).use { it.write(entry) }
        } catch (e: Exception) {
            Log.e("FileLogger", "Failed to write log internally", e)
        }
    }

    private fun sanitize(input: String): String {
        var output = input
        output = PHONE_PATTERN.replace(output) { m ->
            val v = m.value
            "******${v.takeLast(4)}"
        }
        output = OTP_PATTERN.replace(output, "******")
        output = TOKEN_PATTERN.replace(output) { m ->
            val key = m.groupValues[1]
            "$key=...[REDACTED]..."
        }
        return output
    }

    /**
     * If the log file is at or over [MAX_LOG_FILE_BYTES], trim it to the last [TRIM_KEEP_BYTES],
     * keeping content only from a line boundary so no partial line is left at the top.
     * Called from [log] immediately before appending, so the file is capped without extra background work.
     */
    private fun trimLogFileIfNeeded(file: File) {
        if (!file.exists() || file.length() < MAX_LOG_FILE_BYTES) return
        val contentToKeep = try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val toRead = minOf(TRIM_KEEP_BYTES.toLong(), len).toInt()
                raf.seek(len - toRead)
                val bytes = ByteArray(toRead)
                raf.readFully(bytes)
                val content = String(bytes, Charsets.UTF_8)
                val firstNewline = content.indexOf('\n')
                if (firstNewline >= 0) content.substring(firstNewline + 1) else content
            }
        } catch (e: Exception) {
            Log.e("FileLogger", "Trim read failed: ${e.message}", e)
            return
        }
        try {
            FileWriter(file, false).use { it.write(contentToKeep) }
        } catch (e: Exception) {
            Log.e("FileLogger", "Trim write failed: ${e.message}", e)
        }
    }

    fun getLogs(context: Context): String {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) file.readText() else "No logs found."
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun clearLogs(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
            log(context, "FileLogger", "Logs cleared by user.")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, FILE_NAME)
    }

    fun getLastNLines(context: Context, n: Int): String {
        return try {
            val logFile = getLogFile(context)
            if (!logFile.exists() || logFile.length() == 0L) {
                return "No logs found."
            }

            if (logFile.length() < 1024 * 1024) {
                val lines = logFile.readLines()
                return if (lines.isEmpty()) {
                    "No logs found."
                } else {
                    val lastLines = if (lines.size > n) lines.takeLast(n) else lines
                    lastLines.joinToString("\n")
                }
            }

            readLastNLinesFromLargeFile(logFile, n)
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    private fun readLastNLinesFromLargeFile(file: File, n: Int): String {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                if (fileLength == 0L) return "No logs found."

                val lines = mutableListOf<String>()
                val chunkSize = 8192 // Read 8KB chunks
                var position = fileLength
                val buffer = StringBuilder()

                while (position > 0 && lines.size < n) {
                    val readSize = minOf(chunkSize.toLong(), position).toInt()
                    position -= readSize
                    raf.seek(position)

                    val chunk = ByteArray(readSize)
                    raf.readFully(chunk)
                    val chunkText = String(chunk, Charsets.UTF_8)

                    buffer.insert(0, chunkText)

                    val bufferText = buffer.toString()
                    val newLines = bufferText.split("\n")

                    buffer.clear()
                    if (position > 0) {
                        buffer.append(newLines.first())
                    }

                    for (i in newLines.size - 1 downTo if (position > 0) 1 else 0) {
                        if (newLines[i].isNotBlank()) {
                            lines.add(0, newLines[i])
                            if (lines.size >= n) break
                        }
                    }
                }

                return if (lines.isEmpty()) {
                    "No logs found."
                } else {
                    lines.takeLast(n).joinToString("\n")
                }
            }
        } catch (e: Exception) {
            Log.e("FileLogger", "Error reading last N lines", e)
            return "Error reading recent logs: ${e.message}"
        }
    }
}
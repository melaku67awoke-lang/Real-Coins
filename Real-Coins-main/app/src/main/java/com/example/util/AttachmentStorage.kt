package com.example.util

import android.content.Context
import android.util.Base64
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object AttachmentStorage {
    private const val AUTHORITY = "com.example.fileprovider"

    fun persistImage(context: Context, source: Uri, prefix: String): String? {
        return try {
            val dir = File(context.filesDir, "p2p_attachments").apply { mkdirs() }
            val file = File(dir, "${prefix}_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            } ?: return null
            FileProvider.getUriForFile(context, AUTHORITY, file).toString()
        } catch (_: Exception) {
            null
        }
    }


    fun readImageBase64(context: Context, source: Uri): Pair<String, String>? {
        return try {
            val contentType = context.contentResolver.getType(source) ?: "image/jpeg"
            if (contentType !in setOf("image/jpeg", "image/png", "image/webp")) return null
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() } ?: return null
            if (bytes.size > 2 * 1024 * 1024) return null
            contentType to Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun open(context: Context, uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

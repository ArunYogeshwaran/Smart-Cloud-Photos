package com.ayogeshwaran.smartcloudstorage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object FileUtils {
    fun copyMediaToSharedStorage(
        context: Context,
        uris: List<Uri>,
        subfolder: String
    ): List<Uri> {
        val copiedUris = mutableListOf<Uri>()
        for (uri in uris) {
            val displayName = getFileNameFromUri(context, uri) ?: "unnamed_file"
            val mimeType = context.contentResolver.getType(uri)
                ?: "application/octet-stream" // Generic type if unknown

            val newUri = copyFileToSharedStorage(context, uri, displayName, mimeType, subfolder)
            newUri?.let { copiedUris.add(it) }
        }
        return copiedUris
    }

    private fun copyFileToSharedStorage(
        context: Context,
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
        subfolder: String
    ): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$subfolder"
                )
                put(
                    MediaStore.MediaColumns.IS_PENDING,
                    1
                )
            } else {
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    subfolder
                )
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs()
                }
                val file = File(picturesDir, displayName)
                put(MediaStore.MediaColumns.DATA, file.absolutePath)
            }
        }
        val resolver = context.contentResolver
        val collection =
            if (mimeType.startsWith("image/")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }
            } else if (mimeType.startsWith("video/")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
            } else {
                Log.e("MediaStore", "Unsupported mime type: $mimeType")
                return null
            }
        var targetUri: Uri? = null
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            targetUri = resolver.insert(collection, contentValues)
            if (targetUri == null) {
                Log.e("MediaStore", "Failed to create new MediaStore record.")
                return null
            }

            inputStream = resolver.openInputStream(sourceUri)
            outputStream = resolver.openOutputStream(targetUri)

            if (inputStream == null || outputStream == null) {
                Log.e(
                    "MediaStore",
                    "Failed to open streams. Input: $inputStream, Output: $outputStream"
                )
                resolver.delete(targetUri, null, null) // Clean up
                return null
            }

            inputStream.copyTo(outputStream)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(targetUri, contentValues, null, null)
            }
            Log.d("MediaStore", "File copied successfully to: $targetUri")
            return targetUri
        } catch (e: IOException) {
            Log.e("MediaStore", "IOException: ${e.message}", e)
            if (targetUri != null) {
                resolver.delete(targetUri, null, null) // Clean up entry if write failed
            }
            return null
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex =
                        cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (displayNameIndex >= 0) {
                        return cursor.getString(displayNameIndex)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e("MediaStore", "Error getting file name from URI: ${e.message}")
            null
        }
    }

    fun shareMediaWithGooglePhotos(context: Context, fileUris: List<Uri>) {
        if (fileUris.isEmpty()) return

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "image/* video/*" // Or more specific types
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fileUris))
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            `package` = "com.google.android.apps.photos"  // Optional: Target directly
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share to Google Photos"))
    }

    fun uriToFile(uri: Uri, context: Context): File? {
        val contentResolver = context.contentResolver
        var filePath: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            it.moveToFirst()
            val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
            filePath = it.getString(columnIndex)
        }
        return filePath?.let { File(it) }
    }
}
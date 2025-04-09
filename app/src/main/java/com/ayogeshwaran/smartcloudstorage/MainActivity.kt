package com.ayogeshwaran.smartcloudstorage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import com.ayogeshwaran.smartcloudstorage.ui.theme.SmartCloudPhotosTheme
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.quality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

fun copyMediaToSharedStorageAndGetUris(
    context: Context,
    uris: List<Uri>,
    subfolder: String = "SmartCloudPhotos"
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
            ) // Or DIRECTORY_MOVIES
            put(MediaStore.MediaColumns.IS_PENDING, 1) // Mark as pending until write is complete
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
                val displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
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

class MainActivity : ComponentActivity() {

    // Registers a photo picker activity launcher in single-select mode.
    private val pickMedia = registerForActivityResult(PickVisualMedia()) { uri ->
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            compressAndShareMedia(this, listOf(uri))
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    // Registers a photo picker activity launcher in multi-select mode.
    // In this example, the app lets the user select up to 5 media files.
    val pickMultipleMedia =
        registerForActivityResult(PickMultipleVisualMedia(5)) { uris ->
            // Callback is invoked after the user selects media items or closes the
            // photo picker.
            if (uris.isNotEmpty()) {
                compressAndShareMedia(this, uris)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartCloudPhotosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Button(onClick = {
                            pickMedia.launch(
                                PickVisualMediaRequest(
                                    PickVisualMedia.ImageOnly
                                )
                            )
                        }) {
                            Text("Select Media")
                        }

                        Button(onClick = {
                            pickMultipleMedia.launch(
                                PickVisualMediaRequest(
                                    PickVisualMedia.ImageOnly
                                )
                            )
                        }) {
                            Text("Select Multiple Media")
                        }
                    }
                }
            }
        }
    }

    private fun compressAndShareMedia(context: Context, uris: List<Uri>) {
        CoroutineScope(Dispatchers.Main).launch {
            val compressedContentUris = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val file = uriToFile(uri, context)
                    val compressedImageFile = Compressor.compress(this@MainActivity, file!!) {
                        quality(50) // Compress to 50% of the original quality
                    }
                    FileProvider.getUriForFile(
                        this@MainActivity,
                        "${this@MainActivity.packageName}.provider",
                        File(compressedImageFile.path)
                    )
                }
            }
            val sharedStorageContentUris =
                copyMediaToSharedStorageAndGetUris(
                    this@MainActivity.applicationContext,
                    compressedContentUris
                )
            shareMediaWithGooglePhotos(
                this@MainActivity,
                sharedStorageContentUris
            )
        }
    }

    private fun uriToFile(uri: Uri, context: Context): File? {
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SmartCloudPhotosTheme {
        Column {
            Button(onClick = {}) {
                Text("Select Media")
            }
        }
    }
}
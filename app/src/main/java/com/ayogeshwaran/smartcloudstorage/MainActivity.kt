package com.ayogeshwaran.smartcloudstorage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
            val compressedFilePaths = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    val file = uriToFile(uri, context)
                    if (file?.extension in listOf("jpg", "jpeg", "png")) {
                        // Compress image
                        val compressedImageFile = Compressor.compress(this@MainActivity, file!!) {
                            quality(75) // Compress to 75% of the original quality
                        }
                        compressedImageFile.path
                    } else {
                        // For videos, you can implement video compression logic here
                        null
                    }
                }
            }
            shareMediaWithGooglePhotos(compressedFilePaths)
        }
    }

    private fun shareMediaWithGooglePhotos(filePaths: List<String?>) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(filePaths.map { filePath ->
                FileProvider.getUriForFile(
                    this@MainActivity,
                    "${this@MainActivity.packageName}.provider",
                    File(filePath)
                )
            }))
            type = "image/* video/*"
            setPackage("com.google.android.apps.photos")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(Intent.createChooser(shareIntent, "Share to Google Photos"))
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
package com.ayogeshwaran.smartcloudstorage

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import com.ayogeshwaran.smartcloudstorage.FileUtils.copyMediaToSharedStorage
import com.ayogeshwaran.smartcloudstorage.FileUtils.shareMediaWithGooglePhotos
import com.ayogeshwaran.smartcloudstorage.FileUtils.uriToFile
import com.ayogeshwaran.smartcloudstorage.ui.theme.SmartCloudPhotosTheme
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.quality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val pickMedia = registerForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            compressAndShareMedia(this, listOf(uri))
        } else {
            Log.d("PhotoPicker", "No media selected")
        }
    }

    val pickMultipleMedia =
        registerForActivityResult(PickMultipleVisualMedia(MAX_MULTIPLE_MEDIA_COUNT)) { uris ->
            if (uris.isNotEmpty()) {
                compressAndShareMedia(this, uris)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartCloudPhotosTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                        quality(COMPRESSED_IMAGE_QUALITY)
                    }
                    FileProvider.getUriForFile(
                        this@MainActivity,
                        "${this@MainActivity.packageName}.provider",
                        File(compressedImageFile.path)
                    )
                }
            }
            shareMedia(compressedContentUris)
        }
    }

    private fun shareMedia(uris: List<Uri>) {
        val appContext = this.applicationContext
        val sharedStorageContentUris =
            copyMediaToSharedStorage(
                appContext,
                uris,
                appContext.resources.getString(R.string.app_name)
            )
        shareMediaWithGooglePhotos(
            this@MainActivity,
            sharedStorageContentUris
        )
    }

    companion object {
        private const val MAX_MULTIPLE_MEDIA_COUNT = 5
        private const val COMPRESSED_IMAGE_QUALITY = 50
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
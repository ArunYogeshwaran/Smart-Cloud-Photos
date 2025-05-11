package com.ayogeshwaran.smartcloudstorage

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Box
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
import com.ayogeshwaran.smartcloudstorage.FileUtils.uriToFile
import com.ayogeshwaran.smartcloudstorage.ui.screens.HomeScreen
import com.ayogeshwaran.smartcloudstorage.ui.screens.MediaComparisonScreen
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
        setContent {
            SmartCloudPhotosApp()
        }
    }

    @Composable
    fun SmartCloudPhotosApp() {
        SmartCloudPhotosTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(Modifier.padding(innerPadding)) {
                    HomeScreen(
                        onPickMedia = {
                            pickMedia.launch(
                                PickVisualMediaRequest(
                                    PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        onPickMultipleMedia = {
                            pickMultipleMedia.launch(
                                PickVisualMediaRequest(
                                    PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    private fun compressAndShareMedia(context: Context, uris: List<Uri>) {
        CoroutineScope(Dispatchers.Main).launch {
            val mediaComparisonData = withContext(Dispatchers.IO) {
                MediaComparisonData(
                    uris.map { uri ->
                        val file = uriToFile(uri, context)
                        val originalMedia = Media(uri, file!!.length())
                        val compressedImageFile = Compressor.compress(this@MainActivity, file) {
                            quality(COMPRESSED_IMAGE_QUALITY)
                        }
                        val compressedMedia = Media(
                            FileProvider.getUriForFile(
                                this@MainActivity,
                                "${this@MainActivity.packageName}.provider",
                                File(compressedImageFile.path)
                            ),
                            compressedImageFile.length()
                        )
                        originalMedia to compressedMedia
                    }
                )
            }

            setContent {
                MediaComparisonScreen(mediaComparisonData)
            }
        }
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

data class Media(
    val uri: Uri,
    val size: Long
)

data class MediaComparisonData(
    val mediaPairs: List<Pair<Media, Media>>
)
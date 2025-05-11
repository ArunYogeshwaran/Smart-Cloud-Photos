package com.ayogeshwaran.smartcloudstorage.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ayogeshwaran.smartcloudstorage.FileUtils.copyMediaToSharedStorage
import com.ayogeshwaran.smartcloudstorage.FileUtils.shareMediaWithGooglePhotos
import com.ayogeshwaran.smartcloudstorage.MediaComparisonData
import com.ayogeshwaran.smartcloudstorage.R
import com.ayogeshwaran.smartcloudstorage.ui.theme.SmartCloudPhotosTheme

@Composable
fun MediaComparisonScreen(mediaComparisonData: MediaComparisonData) {
    val appContext = LocalContext.current.applicationContext
    SmartCloudPhotosTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(mediaComparisonData.mediaPairs.size) { index ->
                    val (originalMedia, compressedMedia) = mediaComparisonData.mediaPairs[index]
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Original: ${originalMedia.size / 1024} KB")
                            AsyncImage(
                                model = originalMedia.uri,
                                contentDescription = "Original Image",
                                modifier = Modifier.size(150.dp)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Compressed: ${compressedMedia.size / 1024} KB")
                            AsyncImage(
                                model = compressedMedia.uri,
                                contentDescription = "Compressed Image",
                                modifier = Modifier.size(150.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    shareMedia(appContext, mediaComparisonData.mediaPairs.map {
                        it.second.uri
                    })
                },
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text("Share Compressed Media")
            }
        }
    }
}


private fun shareMedia(appContext: Context, uris: List<Uri>) {
    val sharedStorageContentUris =
        copyMediaToSharedStorage(
            appContext,
            uris,
            appContext.resources.getString(R.string.app_name)
        )
    shareMediaWithGooglePhotos(
        appContext,
        sharedStorageContentUris
    )
}

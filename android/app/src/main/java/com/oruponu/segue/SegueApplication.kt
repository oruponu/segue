package com.oruponu.segue

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.oruponu.segue.library.ArtworkFetcher
import com.oruponu.segue.library.ArtworkKeyer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SegueApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ArtworkKeyer())
                add(ArtworkFetcher.Factory(context.contentResolver))
            }
            .build()
}

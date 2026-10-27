package com.oruponu.segue

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.oruponu.segue.analysis.AnalysisCoordinator
import com.oruponu.segue.library.ArtworkFetcher
import com.oruponu.segue.library.ArtworkKeyer
import com.oruponu.segue.library.LibraryRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SegueApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var libraryRepository: LibraryRepository
    @Inject lateinit var analysisCoordinator: AnalysisCoordinator

    override fun onCreate() {
        super.onCreate()
        libraryRepository.startAutoSync()
        analysisCoordinator.start()
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ArtworkKeyer())
                add(ArtworkFetcher.Factory(context.contentResolver))
            }
            .build()
}

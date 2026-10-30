package com.oruponu.segue.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoTest {
    private lateinit var db: SegueDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, SegueDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After fun tearDown() = db.close()

    private fun track(key: String) =
        TrackEntity(
            key = key,
            mediaStoreId = 1L,
            relativePath = "Music/",
            displayName = "$key.mp3",
            title = key,
            album = "Album",
            artist = null,
            albumArtist = null,
            discNumber = null,
            trackNumber = null,
            durationMs = 1000L,
            size = 10L,
            dateModified = 5L,
            albumKey = "ak",
        )

    private fun analysis(key: String) =
        AnalysisEntity(key, 1, AnalysisStatus.OK, true, 120f, 0.9f, 0, 1, 0.8f, 100L)

    private fun styles(key: String, n: Int) = List(n) { TrackStyleEntity(key, it, it * 10, 0.5f) }

    @Test
    fun deletingTrackCascadesToAnalysisAndStyles() = runTest {
        db.trackDao().upsert(listOf(track("a")))
        db.analysisDao().save(analysis("a"), styles("a", 3))
        db.trackDao().delete(listOf("a"))
        assertNull(db.analysisDao().analysis("a"))
        assertEquals(0, db.analysisDao().styles("a").size)
    }

    @Test
    fun saveReplacesOldStyles() = runTest {
        db.trackDao().upsert(listOf(track("a")))
        db.analysisDao().save(analysis("a"), styles("a", 5))
        db.analysisDao().save(analysis("a"), styles("a", 3))
        assertEquals(listOf(0, 1, 2), db.analysisDao().styles("a").map { it.rank })
    }

    @Test
    fun deleteForRemovesAnalysisAndStylesButKeepsTrack() = runTest {
        db.trackDao().upsert(listOf(track("a")))
        db.analysisDao().save(analysis("a"), styles("a", 3))
        db.analysisDao().deleteFor(listOf("a"))
        assertNull(db.analysisDao().analysis("a"))
        assertEquals(0, db.analysisDao().styles("a").size)
        assertNotNull(db.trackDao().byKey("a"))
    }

    @Test
    fun byKeysChunkedReadsEveryChunk() = runTest {
        val keys = List(1000) { "k$it" }
        db.trackDao().upsert(keys.map(::track))
        val found = db.trackDao().byKeysChunked(keys + "missing").map { it.key }
        assertEquals(keys.toSet(), found.toSet())
        assertEquals(keys.size, found.size)
    }

    // Room notifies on its own threads, so this test waits in real time rather than in runTest.
    @Test
    fun observeByKeyFollowsChanges() = runBlocking {
        val dao = db.trackDao()
        val rows = Channel<TrackEntity?>(Channel.UNLIMITED)
        val job =
            launch(Dispatchers.Default) {
                dao.observeByKey("a").distinctUntilChanged().collect { rows.send(it) }
            }
        suspend fun next() = withTimeout(5_000) { rows.receive() }

        assertNull(next())
        dao.upsert(listOf(track("a")))
        assertEquals(track("a"), next())
        dao.upsert(listOf(track("a").copy(size = 20L)))
        assertEquals(20L, next()?.size)
        dao.delete(listOf("a"))
        assertNull(next())
        job.cancel()
    }
}

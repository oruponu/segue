package com.oruponu.segue.analysis

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisCoordinatorTest {
    @Test
    fun forwardsDistinctCurrentKeys() =
        runTest(UnconfinedTestDispatcher()) {
            val calls = mutableListOf<String?>()
            val coordinator =
                AnalysisCoordinator(
                    flowOf(null, "a", "a", "b", null),
                    { calls += it },
                    backgroundScope,
                )

            coordinator.start()

            assertEquals(listOf(null, "a", "b", null), calls)
        }

    @Test
    fun secondStartDoesNotCollectAgain() =
        runTest(UnconfinedTestDispatcher()) {
            val keys = MutableSharedFlow<String?>()
            val calls = mutableListOf<String?>()
            val coordinator = AnalysisCoordinator(keys, { calls += it }, backgroundScope)

            coordinator.start()
            coordinator.start()
            keys.emit("a")

            assertEquals(listOf<String?>("a"), calls)
        }
}

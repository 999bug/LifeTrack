package com.example.lifetrack.feature.home

import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.feature.FakeRecordRepository
import com.example.lifetrack.feature.FakeTypeCatalogRepository
import com.example.lifetrack.feature.record
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    private val dispatcher = StandardTestDispatcher(TestCoroutineScheduler())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cross midnight refresh switches today records`() = runTest(dispatcher) {
        val zone = ZoneId.of("Asia/Shanghai")
        val clock = MutableClock(Instant.parse("2026-11-19T15:59:59Z"), zone)
        val todaySource = TodaySource(clock)
        val repository = FakeRecordRepository().apply {
            recordsForDate.value = listOf(record("old", LocalDate.of(2026, 11, 19)))
        }
        val viewModel = TodayViewModel(repository, FakeTypeCatalogRepository(), todaySource)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.todayRecords.collect()
        }
        runCurrent()
        assertEquals("2026-11-19", repository.requestedDates.last())

        clock.instant = Instant.parse("2026-11-19T16:00:00Z")
        viewModel.refreshToday()
        runCurrent()

        assertEquals("2026-11-20", repository.requestedDates.last())
        viewModel.viewModelScope.cancel()
        runCurrent()
    }

    @Test
    fun `quick record failure is surfaced through action feedback`() = runTest(dispatcher) {
        val repository = FakeRecordRepository().apply {
            addFailure = IllegalStateException("disk full")
        }
        val viewModel = TodayViewModel(repository, FakeTypeCatalogRepository(), TodaySource())

        val message = async { viewModel.actionMessages.first() }
        viewModel.quickRecord()
        runCurrent()

        assertTrue(message.await().contains("disk full"))
        assertEquals(0, repository.addedRecords)
        viewModel.viewModelScope.cancel()
        runCurrent()
    }

    private class MutableClock(
        var instant: Instant,
        private val zone: ZoneId
    ) : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }
}

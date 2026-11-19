package com.example.lifetrack.feature.home

import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.model.HistoryPage
import com.example.lifetrack.domain.model.RecordCursor
import com.example.lifetrack.feature.FakeRecordRepository
import com.example.lifetrack.feature.FakeStatisticsRepository
import com.example.lifetrack.feature.FakeTypeCatalogRepository
import com.example.lifetrack.feature.record
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
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
    fun `keyset pages append until an empty page`() = runTest(dispatcher) {
        val first = record("a", occurredAt = 300L)
        val second = record("b", occurredAt = 200L)
        val third = record("c", occurredAt = 100L)
        val cursorB = RecordCursor(second.event.occurredAtEpochMillis, second.event.id)
        val cursorC = RecordCursor(third.event.occurredAtEpochMillis, third.event.id)
        val repository = FakeRecordRepository().apply {
            pageFactory = { cursor ->
                when (cursor) {
                    null -> HistoryPage(listOf(first, second), cursorB, hasMore = true)
                    cursorB -> HistoryPage(listOf(third), cursorC, hasMore = false)
                    else -> HistoryPage(emptyList(), null, hasMore = false)
                }
            }
        }
        val viewModel = HistoryViewModel(
            repository,
            FakeStatisticsRepository(),
            FakeTypeCatalogRepository(),
            TodaySource()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.historyRecords.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.canLoadEarlier.collect()
        }
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), viewModel.historyRecords.value.map { it.event.id })
        assertTrue(viewModel.canLoadEarlier.value)

        viewModel.loadEarlierHistory()
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), viewModel.historyRecords.value.map { it.event.id })

        viewModel.loadEarlierHistory()
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), viewModel.historyRecords.value.map { it.event.id })
        assertFalse(viewModel.canLoadEarlier.value)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `filter resets paging and marks query as filtered`() = runTest(dispatcher) {
        val repository = FakeRecordRepository().apply {
            pageFactory = { HistoryPage(emptyList(), null, hasMore = false) }
        }
        val viewModel = HistoryViewModel(
            repository,
            FakeStatisticsRepository(),
            FakeTypeCatalogRepository(),
            TodaySource()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.historyRecords.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.canLoadEarlier.collect()
        }
        advanceUntilIdle()

        viewModel.setHistoryFilter(
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 31)
        )
        advanceUntilIdle()

        assertTrue(viewModel.historyQuery.value.filtered)
        assertEquals(LocalDate.of(2026, 10, 1), viewModel.historyQuery.value.start)
        assertEquals(LocalDate.of(2026, 10, 31), viewModel.historyQuery.value.end)
        assertFalse(viewModel.canLoadEarlier.value)
        viewModel.viewModelScope.cancel()
    }
}

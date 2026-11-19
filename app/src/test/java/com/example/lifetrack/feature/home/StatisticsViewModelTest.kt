package com.example.lifetrack.feature.home

import com.example.lifetrack.core.model.StatsPeriod
import androidx.lifecycle.viewModelScope
import com.example.lifetrack.core.time.TodaySource
import com.example.lifetrack.domain.model.DailyCount
import com.example.lifetrack.feature.FakeStatisticsRepository
import com.example.lifetrack.feature.FakeTypeCatalogRepository
import com.example.lifetrack.feature.statistics.observeStatistics
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {
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
    fun `statistics flow and type filter are exposed`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val repository = FakeStatisticsRepository().apply {
            dailyCounts.value = listOf(DailyCount(today.toString(), 2))
        }
        val stats = withTimeout(5_000) {
            observeStatistics(
                repository = repository,
                today = flowOf(today),
                offsets = flowOf(StatsPeriod.entries.associateWith { 0 }),
                typeFilter = flowOf(null)
            ).first { it.getValue(StatsPeriod.WEEK).total == 2 }
        }
        assertEquals(2, stats.getValue(StatsPeriod.WEEK).total)

        val viewModel = StatisticsViewModel(
            repository,
            FakeTypeCatalogRepository(),
            TodaySource()
        )
        viewModel.setStatsTypeFilter("poop")
        assertEquals("poop", viewModel.statsScope.value)
        viewModel.shiftStatistics(StatsPeriod.WEEK, -1)
        advanceUntilIdle()
        assertEquals("poop", viewModel.statsScope.value)
        viewModel.viewModelScope.cancel()
    }
}

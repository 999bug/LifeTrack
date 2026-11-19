package com.example.lifetrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.DEFAULT_TYPES
import com.example.lifetrack.core.database.entity.EventDetailEntity
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDatabaseTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertsGenericEventAndPoopDetail() = runBlocking {
        database.trackTypeDao().insertIgnore(
            TrackTypeEntity("poop", "排便", "poop", "body", "EVENT", true, 10, 1)
        )
        val now = System.currentTimeMillis()
        database.trackEventDao().insertEvent(
            TrackEventEntity("test-id", "poop", now, "2026-11-06", "Asia/Tokyo", "", now, now)
        )
        database.trackEventDao().putPoopDetail(PoopDetailEntity("test-id", bristolType = 4))
        val records = database.trackEventDao().observePoopRecordsForDate("2026-11-06").first()
        assertEquals(1, records.size)
        assertEquals(4, records.single().detail?.bristolType)
    }

    @Test
    fun legacyCoffeeTypeNameIsNormalizedWithoutChangingSettings() = runBlocking {
        database.trackTypeDao().insertIgnore(
            TrackTypeEntity("coffee", "咖啡 / 茶", "coffee", "drink", "EVENT", false, 77, 1)
        )

        com.example.lifetrack.data.repository.RoomTrackRepository(database).ensureDefaultTypes()

        val coffee = database.trackTypeDao().getAll().single { it.code == "coffee" }
        assertEquals("咖啡", coffee.name)
        assertFalse(coffee.enabled)
        assertEquals(77, coffee.sortOrder)
    }

    /** A11：历史 / 月历 / 统计共用的按类型过滤查询，null 表示全部类型。 */
    @Test
    fun typeFilteredQueriesSplitAllFromSingleType() = runBlocking {
        val now = System.currentTimeMillis()
        database.trackTypeDao().insertIgnoreAll(DEFAULT_TYPES)
        database.trackEventDao().insertEvents(
            listOf(
                TrackEventEntity("e-poop", "poop", now, "2026-11-09", "Asia/Tokyo", "", now, now),
                TrackEventEntity("e-water", "water", now, "2026-11-09", "Asia/Tokyo", "", now, now),
                TrackEventEntity("e-old", "water", now - 86_400_000L, "2026-11-08", "Asia/Tokyo", "", now, now)
            )
        )

        val all = database.trackEventDao()
            .observeRecordsInRange("2026-11-08", "2026-11-09", typeCode = null).first()
        assertEquals(3, all.size)

        val water = database.trackEventDao()
            .observeRecordsInRange("2026-11-08", "2026-11-09", typeCode = "water").first()
        assertEquals(2, water.size)
        assertEquals(setOf("e-water", "e-old"), water.map { it.event.id }.toSet())

        val allCounts = database.trackEventDao()
            .observeDailyCounts("2026-11-08", "2026-11-09", typeCode = null).first()
        assertEquals(2, allCounts.first { it.localDate == "2026-11-09" }.count)
        assertEquals(1, allCounts.first { it.localDate == "2026-11-08" }.count)

        val poopCounts = database.trackEventDao()
            .observeDailyCounts("2026-11-08", "2026-11-09", typeCode = "poop").first()
        assertEquals(1, poopCounts.single { it.localDate == "2026-11-09" }.count)
        assertEquals(1, poopCounts.size)

        val allStamps = database.trackEventDao()
            .observeEventTimestamps("2026-11-08", "2026-11-09", typeCode = null).first()
        assertEquals(3, allStamps.size)
        val poopStamps = database.trackEventDao()
            .observeEventTimestamps("2026-11-08", "2026-11-09", typeCode = "poop").first()
        assertEquals(1, poopStamps.size)
    }

    /** Task 7：同毫秒记录也必须由 (occurredAt, id) 游标完整分页，不重复、不漏数。 */
    @Test
    fun keysetHistoryPaginationHandlesDuplicateTimestampsAndCrossesDates() = runBlocking {
        database.trackTypeDao().insertIgnoreAll(DEFAULT_TYPES)
        val now = System.currentTimeMillis()
        database.trackEventDao().insertEvents(
            listOf(
                TrackEventEntity("e-c", "poop", 3_000L, "2026-11-19", "Asia/Tokyo", "", now, now),
                TrackEventEntity("e-b", "poop", 2_000L, "2026-11-19", "Asia/Tokyo", "", now, now),
                TrackEventEntity("e-a", "poop", 2_000L, "2026-11-19", "Asia/Tokyo", "", now, now),
                TrackEventEntity("e-old", "poop", 1_000L, "2026-11-18", "Asia/Tokyo", "", now, now)
            )
        )
        val repository = com.example.lifetrack.data.repository.RoomTrackRepository(database)

        val first = repository.observeHistoryPage(
            startDate = "2026-11-18",
            endDate = "2026-11-19",
            typeCode = null,
            cursor = null,
            limit = 2
        ).first()
        assertEquals(listOf("e-c", "e-b"), first.records.map { it.event.id })
        assertTrue(first.hasMore)

        val second = repository.observeHistoryPage(
            startDate = "2026-11-18",
            endDate = "2026-11-19",
            typeCode = null,
            cursor = first.nextCursor,
            limit = 2
        ).first()
        assertEquals(listOf("e-a", "e-old"), second.records.map { it.event.id })
        assertFalse(second.hasMore)
        assertEquals(4, (first.records + second.records).map { it.event.id }.toSet().size)
    }
    /** A12：通用详情写入后能按事件读回，外键级联删除不留孤儿行。 */
    @Test
    fun genericDetailRoundTripsAndCascades() = runBlocking {
        val now = System.currentTimeMillis()
        database.trackTypeDao().insertIgnoreAll(DEFAULT_TYPES)
        database.trackEventDao().insertEvent(
            TrackEventEntity("e-coffee", "coffee", now, "2026-11-09", "Asia/Tokyo", "", now, now)
        )
        database.trackEventDao().putEventDetail(
            EventDetailEntity(
                eventId = "e-coffee",
                typeCode = "coffee",
                payload = """{"drinkType":"tea","amountMl":"300"}"""
            )
        )
        val records = database.trackEventDao().observeRecordsForDate("2026-11-09").first()
        assertEquals("coffee", records.single().generic?.typeCode)
        assertEquals("""{"drinkType":"tea","amountMl":"300"}""", records.single().generic?.payload)

        database.trackEventDao().deleteEvent("e-coffee")
        assertEquals(0, database.trackEventDao().observeRecordsForDate("2026-11-09").first().size)
    }
}

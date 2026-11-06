package com.example.lifetrack

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import com.example.lifetrack.core.database.AppDatabase
import com.example.lifetrack.core.database.entity.PoopDetailEntity
import com.example.lifetrack.core.database.entity.TrackEventEntity
import com.example.lifetrack.core.database.entity.TrackTypeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
}

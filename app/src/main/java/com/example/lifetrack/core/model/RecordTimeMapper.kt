package com.example.lifetrack.core.model

import java.time.Instant
import java.time.ZoneId

/** Converts one instant into the local calendar date that was valid in a given zone. */
object RecordTimeMapper {
    data class Metadata(
        val occurredAtEpochMillis: Long,
        val localDate: String,
        val zoneId: String
    )

    fun from(epochMillis: Long, zoneId: String): Metadata {
        val zone = ZoneId.of(zoneId)
        val localDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()
        return Metadata(
            occurredAtEpochMillis = epochMillis,
            localDate = localDate,
            zoneId = zone.id
        )
    }
}

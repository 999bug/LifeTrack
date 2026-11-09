package com.example.lifetrack.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 通用详情表：除排便外的所有记录类型共用，避免每加一个模块就升 Room 版本。
 *
 * [payload] 是 `RecordDetail.Generic.fields` 的 JSON 对象（Map<String, String>），
 * 字段合法性由 `DetailSpecRegistry` 在备份解码与写入前校验，数据库不解释内容。
 */
@Entity(
    tableName = "event_detail",
    foreignKeys = [
        ForeignKey(
            entity = TrackEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["typeCode"])]
)
data class EventDetailEntity(
    @PrimaryKey val eventId: String,
    val typeCode: String,
    val payload: String
)

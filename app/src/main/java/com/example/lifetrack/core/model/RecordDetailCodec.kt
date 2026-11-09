package com.example.lifetrack.core.model

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * `event_detail.payload` 与 `RecordDetail.Generic.fields` 之间的转换。
 *
 * 用显式 MapSerializer，不走 reified 扩展：与 BackupCodec 保持同一套解析约定，
 * 也避免数据库里出现不可解析的野值。解析失败返回 null 表示「没有可用详情」，
 * 不让一条坏 payload 把整个列表渲染打挂。
 */
object RecordDetailCodec {
    private val json = Json { encodeDefaults = true }
    private val fieldsSerializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(fields: Map<String, String>): String =
        json.encodeToString(fieldsSerializer, fields)

    fun decode(payload: String): Map<String, String>? =
        runCatching { json.decodeFromString(fieldsSerializer, payload) }.getOrNull()
}

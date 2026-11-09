package com.example.lifetrack.core.model

import com.example.lifetrack.core.database.entity.TrackTypeEntity

/**
 * 记录项目排序：把指定 code 在按 sortOrder 排好的列表里移动一位，
 * 并把 sortOrder 重排成 10、20、30…，避免多次移动后出现重复值。
 * 纯函数，无 Android 依赖，可直接单测。
 */
object ModuleOrdering {

    /** [offset] 为 -1 上移、+1 下移；越界或找不到 code 时原样返回。 */
    fun move(types: List<TrackTypeEntity>, code: String, offset: Int): List<TrackTypeEntity> {
        if (offset == 0) return types
        val ordered = types.sortedBy { it.sortOrder }
        val from = ordered.indexOfFirst { it.code == code }
        if (from < 0) return types
        val to = from + offset
        if (to !in ordered.indices) return types

        val swapped = ordered.toMutableList()
        val item = swapped.removeAt(from)
        swapped.add(to, item)
        return swapped.mapIndexed { index, type -> type.copy(sortOrder = (index + 1) * 10) }
    }
}

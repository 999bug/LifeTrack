package com.example.lifetrack.feature.poop

/**
 * 布里斯托形态量表 1–7 型的展示文案。
 *
 * 这里只描述外形与稠度，不写诊断结论；存储的仍是 [Int]?（见 PoopDetailEntity.bristolType），
 * 备份范围校验（1–7）也不因新增文案而改变。分组词是含水量轴上的位置，不是医学分型。
 */
data class BristolType(
    val number: Int,
    val shortLabel: String,
    val group: String,
    val description: String
)

object BristolCatalog {

    const val MIN = 1
    const val MAX = 7

    const val GROUP_DRY = "偏干端"
    const val GROUP_MIDDLE = "中间段"
    const val GROUP_LOOSE = "偏稀端"

    val groups: List<Pair<String, IntRange>> = listOf(
        GROUP_DRY to 1..2,
        GROUP_MIDDLE to 3..4,
        GROUP_LOOSE to 5..7
    )

    val all: List<BristolType> = listOf(
        BristolType(1, "分离硬球", GROUP_DRY, "一颗颗分离的硬球，像坚果，很难排出"),
        BristolType(2, "凹凸结块", GROUP_DRY, "连成香肠状，但表面凹凸不平、有明显结块"),
        BristolType(3, "带裂痕", GROUP_MIDDLE, "香肠状，表面有裂痕，偏干一些"),
        BristolType(4, "光滑柔软", GROUP_MIDDLE, "香肠或蛇形，表面光滑柔软，容易排出"),
        BristolType(5, "柔软小块", GROUP_LOOSE, "断边清楚的柔软小块，很容易排出"),
        BristolType(6, "糊状", GROUP_LOOSE, "糊状、蓬松，边缘毛糙不规则"),
        BristolType(7, "水样", GROUP_LOOSE, "完全是水样，没有固体块")
    )

    /** 越界返回 null，调用方据此回到「未记录」而不是崩在数组下标。 */
    fun of(number: Int): BristolType? =
        all.getOrNull(number - MIN)?.takeIf { it.number == number }
}

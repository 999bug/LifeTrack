package com.example.lifetrack.core.security

/**
 * 备份口令强度评估：只做「够不够长、字符种类够不够多」的粗判，不假装能算出真实熵。
 *
 * 目标是给用户一个可行动的提示（再长一点 / 加个数字），而不是打分排名；
 * 评估结果不参与加解密，也不会改变任何密钥派生参数。
 *
 * 纯 JVM 实现，便于在单测里固定住分档边界。
 */
object PassphraseQuality {

    /** 与导出流程共用的最小长度，避免两处常量各写一遍后漂移。 */
    const val MIN_LENGTH = 8

    enum class Level {
        TOO_SHORT,
        WEAK,
        FAIR,
        STRONG
    }

    data class Result(val level: Level, val score: Int)

    /**
     * 分数 = 长度 + 2 × 字符种类数（小写 / 大写 / 数字 / 符号或空格 / 非 ASCII）。
     * - 低于 13：偏弱
     * - 13–17：可用
     * - 18 及以上：较强
     *
     * 中文口令按「非 ASCII」计一种字符类，长度仍按字符数算；
     * 一句 12 字的中文口令会落在「可用」，不会被误判成弱口令。
     */
    fun evaluate(passphrase: CharArray): Result {
        if (passphrase.size < MIN_LENGTH) return Result(Level.TOO_SHORT, passphrase.size)

        var hasLower = false
        var hasUpper = false
        var hasDigit = false
        var hasSymbol = false
        var hasNonAscii = false
        for (c in passphrase) {
            when {
                c in 'a'..'z' -> hasLower = true
                c in 'A'..'Z' -> hasUpper = true
                c in '0'..'9' -> hasDigit = true
                c.code > 0x7F -> hasNonAscii = true
                else -> hasSymbol = true
            }
        }
        val classes = listOf(hasLower, hasUpper, hasDigit, hasSymbol, hasNonAscii).count { it }
        val score = passphrase.size + classes * 2
        val level = when {
            score >= 18 -> Level.STRONG
            score >= 13 -> Level.FAIR
            else -> Level.WEAK
        }
        return Result(level, score)
    }
}

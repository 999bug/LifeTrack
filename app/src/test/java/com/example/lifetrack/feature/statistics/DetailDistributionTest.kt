package com.example.lifetrack.feature.statistics

import com.example.lifetrack.core.database.model.StatDetailProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailDistributionTest {

    private fun row(
        bristol: Int? = null,
        amount: Int? = null,
        effort: Int? = null,
        urgency: Int? = null,
        abdominal: Int? = null,
        incomplete: Int? = null
    ) = StatDetailProjection(
        localDate = "2026-11-09",
        bristolType = bristol,
        amountCode = amount,
        effortCode = effort,
        urgency = urgency,
        abdominalDiscomfort = abdominal,
        incompleteEvacuation = incomplete
    )

    @Test
    fun `null fields are not counted into any bucket`() {
        val distribution = DetailDistribution.from(listOf(row(), row(bristol = 3)), 2)

        assertEquals(2, distribution.totalRecords)
        assertEquals(1, distribution.filledBristol)
        assertEquals(listOf(0, 0, 1, 0, 0, 0, 0), distribution.bristolCounts)
        assertEquals(1, distribution.bristolCounts.sum())
        assertEquals(0, distribution.filledAmount)
        assertTrue(distribution.anyFilled)
    }

    @Test
    fun `illegal values are ignored entirely`() {
        val distribution = DetailDistribution.from(
            listOf(
                row(bristol = 9, amount = 0, effort = 4, urgency = 2, abdominal = -1),
                row(incomplete = 9)
            ),
            2
        )

        assertEquals(0, distribution.filledBristol)
        assertEquals(0, distribution.filledAmount)
        assertEquals(0, distribution.filledEffort)
        assertEquals(0, distribution.filledUrgency)
        assertEquals(0, distribution.filledAbdominal)
        assertEquals(0, distribution.filledIncomplete)
        assertEquals(0, distribution.bristolCounts.sum())
        assertFalse(distribution.anyFilled)
    }

    @Test
    fun `each field keeps its own denominator`() {
        val distribution = DetailDistribution.from(
            listOf(row(bristol = 4), row(amount = 2), row(effort = 3)),
            3
        )

        assertEquals(3, distribution.totalRecords)
        assertEquals(1, distribution.filledBristol)
        assertEquals(1, distribution.filledAmount)
        assertEquals(1, distribution.filledEffort)
        assertEquals(listOf(0, 0, 0, 1, 0, 0, 0), distribution.bristolCounts)
        assertEquals(listOf(0, 1, 0), distribution.amountCounts)
        assertEquals(listOf(0, 0, 1), distribution.effortCounts)
    }

    @Test
    fun `sensations count only true values`() {
        val distribution = DetailDistribution.from(
            listOf(
                row(urgency = 1, abdominal = 0, incomplete = 1),
                row(urgency = 0)
            ),
            2
        )

        assertEquals(1, distribution.urgencyCount)
        assertEquals(2, distribution.filledUrgency)
        assertEquals(0, distribution.abdominalCount)
        assertEquals(1, distribution.filledAbdominal)
        assertEquals(1, distribution.incompleteCount)
        assertEquals(1, distribution.filledIncomplete)
    }

    @Test
    fun `quick records without details produce a zero filled distribution`() {
        val distribution = DetailDistribution.from(listOf(row(), row(), row()), 3)

        assertEquals(3, distribution.totalRecords)
        assertEquals(0, distribution.filledBristol)
        assertFalse(distribution.anyFilled)
    }

    @Test
    fun `empty input keeps fixed bucket sizes`() {
        val distribution = DetailDistribution.from(emptyList(), 0)

        assertEquals(0, distribution.totalRecords)
        assertEquals(7, distribution.bristolCounts.size)
        assertEquals(3, distribution.amountCounts.size)
        assertEquals(3, distribution.effortCounts.size)
        assertFalse(distribution.anyFilled)
    }
}

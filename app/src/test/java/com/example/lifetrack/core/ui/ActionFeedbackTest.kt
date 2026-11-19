package com.example.lifetrack.core.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionFeedbackTest {
    @Test
    fun failedActionEmitsReadableMessage() = runBlocking {
        val feedback = ActionFeedback()
        val scope = CoroutineScope(Dispatchers.Unconfined)

        scope.launchAction(feedback, "保存失败") {
            error("database is locked")
        }

        assertEquals("保存失败：database is locked", feedback.messages.first())
    }
}

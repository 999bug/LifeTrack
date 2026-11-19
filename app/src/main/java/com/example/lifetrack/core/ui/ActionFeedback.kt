package com.example.lifetrack.core.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 一次性写操作反馈；页面用 Snackbar 或错误弹窗消费。 */
class ActionFeedback {
    private val channel = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = channel.receiveAsFlow()

    fun report(message: String) {
        channel.trySend(message)
    }
}

/** 统一写操作异常兜底：保留取消语义，其余异常转成页面可读消息。 */
fun CoroutineScope.launchAction(
    feedback: ActionFeedback,
    failurePrefix: String,
    block: suspend () -> Unit
) {
    launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            feedback.report("$failurePrefix：${e.message ?: "unknown error"}")
        }
    }
}

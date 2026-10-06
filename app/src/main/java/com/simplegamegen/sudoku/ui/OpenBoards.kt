package com.simplegamegen.sudoku.ui

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin

/**
 * Games currently held in memory. Settings wipes their boards before deleting the saves, so a
 * screen that is still open cannot write the game back.
 */
internal object OpenBoards {
    private val boards = CopyOnWriteArrayList<suspend () -> Unit>()

    fun watch(scope: CoroutineScope, forget: suspend () -> Unit) {
        boards.add(forget)
        scope.coroutineContext[Job]?.invokeOnCompletion { boards.remove(forget) }
    }

    suspend fun forgetAll() {
        for (forget in boards.toList()) forget()
    }
}

/** Cancels work still running for a game, including a save started while the first wave was stopping. */
internal suspend fun drainChildren(scope: CoroutineScope) {
    repeat(4) {
        val children = scope.coroutineContext[Job]?.children?.toList().orEmpty()
        if (children.isEmpty()) return
        for (child in children) child.cancelAndJoin()
    }
}

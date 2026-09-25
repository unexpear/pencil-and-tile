package com.simplegamegen.sudoku.ui.tutorial

import com.simplegamegen.sudoku.ui.GameId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TutorialsTest {
    @Test fun `every game has its own tutorial with rules and guided steps`() {
        val all = GameId.entries.map { Tutorials.of(it) }
        GameId.entries.forEachIndexed { i, id -> assertEquals(id, all[i].game) }
        all.forEach { t ->
            assertTrue(t.rules.size >= 3, "${t.game} lists its rules")
            assertTrue(t.steps.size >= 3, "${t.game} has guided steps")
            assertTrue(t.steps.count { it.interactive } >= 1, "${t.game} lets you play")
            assertTrue(t.summary.isNotBlank())
        }
        assertEquals(GameId.entries.size, all.map { it.rules }.toSet().size, "rules aren't shared between games")
    }

    @Test fun `every step's targets exist and scenes are well formed`() {
        GameId.entries.forEach { id ->
            Tutorials.of(id).steps.forEachIndexed { n, step ->
                val where = "$id step ${n + 1}"
                listOfNotNull(step.scene, step.after).forEach { scene ->
                    val ids = scene.items.map { it.id }.filter { it.isNotEmpty() }
                    assertEquals(ids.size, ids.toSet().size, "$where has duplicate item ids")
                    assertTrue(scene.width > 0 && scene.height > 0, where)
                    scene.items.forEach { assertTrue(it.x >= -0.01f && it.y >= -0.01f && it.x + it.w <= scene.width + 0.01f && it.y + it.h <= scene.height + 0.01f,
                        "$where item ${it.id} ${it.look} is outside the scene") }
                    scene.links.forEach { assertTrue(scene.item(it.from) != null && scene.item(it.to) != null, "$where link ends exist") }
                }
                step.tap.forEach { assertTrue(step.scene.item(it) != null, "$where tap target $it exists") }
                step.pick?.let { assertTrue(it in step.scene.choices, "$where choice $it is offered") }
                assertTrue(!(step.tap.isNotEmpty() && step.pick != null), "$where asks for one action")
            }
        }
    }
}

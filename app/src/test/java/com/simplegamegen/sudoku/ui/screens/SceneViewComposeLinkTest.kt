package com.simplegamegen.sudoku.ui.screens

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * SceneView 4.18 composables were compiled against Compose BOM 2026.05.01.
 * `rememberEngine` calls these on the first composition of Knife Flip. Compose 1.7
 * does not declare them, so the screen crashed before a frame.
 */
class SceneViewComposeLinkTest {
    @Test fun `compose runtime still has the methods SceneView calls on open`() {
        val composer = Class.forName("androidx.compose.runtime.Composer")
        assertNotNull(composer.getMethod("shouldExecute", Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        val composables = Class.forName("androidx.compose.runtime.ComposablesKt")
        assertNotNull(composables.getMethod("getCurrentCompositeKeyHashCode", composer, Int::class.javaPrimitiveType))
        // The older compiler still emits this. Dropping it would crash every other game.
        assertNotNull(composables.getMethod("getCurrentCompositeKeyHash", composer, Int::class.javaPrimitiveType))
        val node = Class.forName("androidx.compose.ui.node.ComposeUiNode\$Companion")
        assertNotNull(node.getMethod("getApplyOnDeactivatedNodeAssertion"))
    }
}

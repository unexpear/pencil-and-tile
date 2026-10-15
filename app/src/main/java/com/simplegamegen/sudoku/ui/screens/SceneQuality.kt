package com.simplegamegen.sudoku.ui.screens

import com.google.android.filament.View

/**
 * Crisp 3D for the Mancala and Knife Flip views.
 *
 * SceneView's RenderQuality presets leave MSAA off and smooth the frame with FXAA, half-res
 * SSAO and bloom. On a phone that reads as soft, smeared models. This keeps the frame at the
 * surface's native size (no dynamic resolution) with 4x MSAA and no post blur, and keeps
 * shadows. SceneView applies its preset when the view is set up, so call [keepCrisp] each
 * frame; it only writes options when they have been reset.
 */
internal fun View.keepCrisp() {
    val msaa = multiSampleAntiAliasingOptions
    if (msaa.enabled && msaa.sampleCount == MSAA_SAMPLES && antiAliasing == View.AntiAliasing.NONE &&
        !dynamicResolutionOptions.enabled && !bloomOptions.enabled && !ambientOcclusionOptions.enabled
    ) return
    dynamicResolutionOptions = dynamicResolutionOptions.apply { enabled = false }
    multiSampleAntiAliasingOptions = msaa.apply {
        enabled = true
        sampleCount = MSAA_SAMPLES
        customResolve = false
    }
    antiAliasing = View.AntiAliasing.NONE
    ambientOcclusionOptions = ambientOcclusionOptions.apply { enabled = false }
    bloomOptions = bloomOptions.apply { enabled = false }
    renderQuality = renderQuality.apply { hdrColorBuffer = View.QualityLevel.HIGH }
    setShadowingEnabled(true)
    setShadowType(View.ShadowType.PCF)
}

internal const val MSAA_SAMPLES = 4

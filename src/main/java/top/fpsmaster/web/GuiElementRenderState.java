// Copyright (c) 2026 FPSMaster Team
// SPDX-License-Identifier: MIT
// This file is part of FPSMaster Nova. See LICENSE for details.

package top.fpsmaster.web;

import org.joml.Matrix3x2f;

/**
 * Browser GUI render-state marker for the modern (1.21.5+) submit-node pipeline.
 *
 * <p>Extends vanilla {@link net.minecraft.client.gui.render.state.GuiElementRenderState}
 * so instances can be queued via {@code GuiRenderState#submitGuiElement}. FPSMaster
 * currently uses a single implementation — {@link TexQuadGuiElementRenderState} — which
 * draws the CEF frame as a textured quad.</p>
 */
public sealed interface GuiElementRenderState
        extends net.minecraft.client.gui.render.state.GuiElementRenderState
        permits TexQuadGuiElementRenderState {

    /**
     * 2D pose transform for this element.
     * The matrix is owned by the submitter and must not be retained after submission.
     */
    Matrix3x2f pose();
}

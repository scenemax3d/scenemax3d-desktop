package com.scenemaxeng.common.ui.model;

/**
 * Determines how a UI layer is rendered.
 *
 * SCREEN_SPACE - rendered as a 2D overlay via JME's guiNode (orthographic projection).
 *                Ideal for HUD, menus, inventory screens.
 * WORLD_SPACE  - rendered as a 3D billboard in the scene.
 *                Ideal for health bars above characters, in-game screens, etc.
 */
public enum UIRenderMode {
    SCREEN_SPACE,
    WORLD_SPACE
}

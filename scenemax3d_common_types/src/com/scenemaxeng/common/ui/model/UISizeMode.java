package com.scenemaxeng.common.ui.model;

/**
 * Sizing modes for UI widgets, inspired by Android ConstraintLayout.
 *
 * FIXED        - explicit pixel size set by the user
 * WRAP_CONTENT - widget sizes itself to fit its content (text length, image size, etc.)
 * MATCH_CONSTRAINT - widget expands to fill available space between its constraints (0dp in Android terms)
 */
public enum UISizeMode {
    FIXED,
    WRAP_CONTENT,
    MATCH_CONSTRAINT
}

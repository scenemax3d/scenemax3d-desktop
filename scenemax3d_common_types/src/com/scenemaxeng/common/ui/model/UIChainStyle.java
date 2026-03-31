package com.scenemaxeng.common.ui.model;

/**
 * Chain distribution styles, following Android ConstraintLayout conventions.
 *
 * SPREAD         - widgets are evenly distributed with equal spacing (including edges)
 * SPREAD_INSIDE  - widgets are evenly distributed; first and last touch the chain edges
 * PACKED         - widgets are packed together in the center (or biased position)
 */
public enum UIChainStyle {
    SPREAD,
    SPREAD_INSIDE,
    PACKED
}

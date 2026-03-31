package com.scenemaxeng.common.ui.layout;

import com.scenemaxeng.common.ui.model.*;

import java.util.*;

/**
 * Constraint layout solver inspired by Android's ConstraintLayout.
 * Takes a list of widgets with constraints and computes their final positions/sizes
 * within a given parent bounds.
 *
 * This class is pure math — no JME dependency — and can be unit tested in isolation.
 *
 * Layout algorithm overview:
 * 1. Build a dependency graph from constraints
 * 2. Topological sort to determine resolution order (detect cycles)
 * 3. For each widget, resolve horizontal then vertical position/size:
 *    - If constrained on one side: anchor to that side + margin
 *    - If constrained on both sides: position using bias between anchors
 *    - If unconstrained: default to parent origin (0,0)
 * 4. Handle chains: distribute space among chain members
 * 5. Handle aspect ratio: compute one dimension from the other
 *
 * Coordinate system: origin is top-left, X grows right, Y grows down.
 */
public class ConstraintLayoutEngine {

    /**
     * Computes layout rectangles for all widgets in a layer.
     *
     * @param widgets     the flat list of widgets to lay out (direct children of the container)
     * @param parentWidth  width of the parent container in pixels
     * @param parentHeight height of the parent container in pixels
     * @return map from widget name to computed LayoutRect
     */
    public Map<String, LayoutRect> solve(List<UIWidgetDef> widgets, float parentWidth, float parentHeight) {
        Map<String, LayoutRect> results = new LinkedHashMap<>();
        Map<String, UIWidgetDef> widgetMap = new LinkedHashMap<>();

        for (UIWidgetDef w : widgets) {
            widgetMap.put(w.getName(), w);
            results.put(w.getName(), new LayoutRect());
        }

        // Resolve guidelines first (they have no dependencies on other widgets)
        for (UIWidgetDef w : widgets) {
            if (w.getType() == UIWidgetType.GUIDELINE) {
                resolveGuideline(w, results.get(w.getName()), parentWidth, parentHeight);
            }
        }

        // Topological sort based on constraint dependencies
        List<UIWidgetDef> sorted = topologicalSort(widgets, widgetMap);

        // Resolve each widget in dependency order
        for (UIWidgetDef w : sorted) {
            if (w.getType() == UIWidgetType.GUIDELINE) continue; // already resolved

            LayoutRect rect = results.get(w.getName());
            resolveWidget(w, rect, widgetMap, results, parentWidth, parentHeight);
        }

        // Resolve chains (must be done after initial positioning)
        resolveChains(widgets, widgetMap, results, parentWidth, parentHeight);

        return results;
    }

    /**
     * Recursively solves layout for a widget and all its children.
     * Call this for widgets that are containers (PANELs with children).
     *
     * @param widget       the container widget
     * @param parentRect   the computed LayoutRect of the container
     * @param allResults   accumulates all computed rects (including children)
     */
    public void solveChildren(UIWidgetDef widget, LayoutRect parentRect,
                              Map<String, LayoutRect> allResults) {
        if (widget.getChildren().isEmpty()) return;

        // The child layout space accounts for padding
        float innerWidth = parentRect.width - widget.getPaddingLeft() - widget.getPaddingRight();
        float innerHeight = parentRect.height - widget.getPaddingTop() - widget.getPaddingBottom();

        Map<String, LayoutRect> childResults = solve(widget.getChildren(), innerWidth, innerHeight);

        // Offset children to parent's position + padding
        float offsetX = parentRect.x + widget.getPaddingLeft();
        float offsetY = parentRect.y + widget.getPaddingTop();

        for (Map.Entry<String, LayoutRect> entry : childResults.entrySet()) {
            LayoutRect childRect = entry.getValue();
            childRect.x += offsetX;
            childRect.y += offsetY;
            allResults.put(entry.getKey(), childRect);
        }

        // Recurse into children that have their own children
        for (UIWidgetDef child : widget.getChildren()) {
            if (!child.getChildren().isEmpty() && allResults.containsKey(child.getName())) {
                solveChildren(child, allResults.get(child.getName()), allResults);
            }
        }
    }

    // ========================================================================
    // Internal: guideline resolution
    // ========================================================================

    private void resolveGuideline(UIWidgetDef guideline, LayoutRect rect,
                                  float parentWidth, float parentHeight) {
        float pos;
        if (guideline.isGuidelinePercent()) {
            // Position is a 0-1 ratio
            if (guideline.isGuidelineHorizontal()) {
                pos = guideline.getGuidelinePosition() * parentHeight;
            } else {
                pos = guideline.getGuidelinePosition() * parentWidth;
            }
        } else {
            pos = guideline.getGuidelinePosition();
        }

        if (guideline.isGuidelineHorizontal()) {
            // Horizontal guideline: a horizontal line at Y = pos
            rect.x = 0;
            rect.y = pos;
            rect.width = parentWidth;
            rect.height = 0;
        } else {
            // Vertical guideline: a vertical line at X = pos
            rect.x = pos;
            rect.y = 0;
            rect.width = 0;
            rect.height = parentHeight;
        }
    }

    // ========================================================================
    // Internal: topological sort
    // ========================================================================

    private List<UIWidgetDef> topologicalSort(List<UIWidgetDef> widgets,
                                               Map<String, UIWidgetDef> widgetMap) {
        // Build adjacency: widget -> set of widgets it depends on
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        for (UIWidgetDef w : widgets) {
            Set<String> widgetDeps = new LinkedHashSet<>();
            for (UIConstraint c : w.getConstraints()) {
                if (!c.isParentConstraint() && widgetMap.containsKey(c.getTargetName())) {
                    widgetDeps.add(c.getTargetName());
                }
            }
            deps.put(w.getName(), widgetDeps);
        }

        // Kahn's algorithm
        List<UIWidgetDef> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (UIWidgetDef w : widgets) {
            if (!visited.contains(w.getName())) {
                if (!dfs(w.getName(), deps, widgetMap, visited, visiting, sorted)) {
                    // Cycle detected — fall back to original order
                    System.err.println("[UILayout] Cycle detected in constraints, using original order");
                    return new ArrayList<>(widgets);
                }
            }
        }

        return sorted;
    }

    private boolean dfs(String name, Map<String, Set<String>> deps,
                        Map<String, UIWidgetDef> widgetMap,
                        Set<String> visited, Set<String> visiting,
                        List<UIWidgetDef> sorted) {
        if (visiting.contains(name)) return false; // cycle
        if (visited.contains(name)) return true;

        visiting.add(name);

        Set<String> widgetDeps = deps.get(name);
        if (widgetDeps != null) {
            for (String dep : widgetDeps) {
                if (!dfs(dep, deps, widgetMap, visited, visiting, sorted)) return false;
            }
        }

        visiting.remove(name);
        visited.add(name);
        UIWidgetDef w = widgetMap.get(name);
        if (w != null) sorted.add(w);
        return true;
    }

    // ========================================================================
    // Internal: single widget resolution
    // ========================================================================

    private void resolveWidget(UIWidgetDef widget, LayoutRect rect,
                                Map<String, UIWidgetDef> widgetMap,
                                Map<String, LayoutRect> results,
                                float parentWidth, float parentHeight) {

        // --- Horizontal axis ---
        resolveAxis(widget, rect, widgetMap, results, parentWidth, true);

        // --- Vertical axis ---
        resolveAxis(widget, rect, widgetMap, results, parentHeight, false);

        // --- Aspect ratio ---
        if (widget.getAspectRatio() > 0) {
            resolveAspectRatio(widget, rect);
        }
    }

    private void resolveAxis(UIWidgetDef widget, LayoutRect rect,
                              Map<String, UIWidgetDef> widgetMap,
                              Map<String, LayoutRect> results,
                              float parentSize, boolean horizontal) {

        UIConstraintSide startSide = horizontal ? UIConstraintSide.LEFT : UIConstraintSide.TOP;
        UIConstraintSide endSide = horizontal ? UIConstraintSide.RIGHT : UIConstraintSide.BOTTOM;

        UIConstraint startConstraint = widget.getConstraintForSide(startSide);
        UIConstraint endConstraint = widget.getConstraintForSide(endSide);

        UISizeMode sizeMode = horizontal ? widget.getWidthMode() : widget.getHeightMode();
        float fixedSize = horizontal ? widget.getWidth() : widget.getHeight();
        float bias = horizontal ? widget.getHorizontalBias() : widget.getVerticalBias();

        // Resolve anchor positions
        float startAnchor = resolveAnchorPosition(startConstraint, results, parentSize, horizontal);
        float endAnchor = resolveAnchorPosition(endConstraint, results, parentSize, horizontal);

        boolean hasStart = startConstraint != null;
        boolean hasEnd = endConstraint != null;

        float startMargin = hasStart ? startConstraint.getMargin() : 0;
        float endMargin = hasEnd ? endConstraint.getMargin() : 0;

        float size;
        float position;

        if (hasStart && hasEnd) {
            // Constrained on both sides
            float availableSpace = endAnchor - startAnchor - startMargin - endMargin;

            switch (sizeMode) {
                case MATCH_CONSTRAINT:
                    // Fill available space
                    size = Math.max(0, availableSpace);
                    position = startAnchor + startMargin;
                    break;
                case FIXED:
                    size = fixedSize;
                    // Center with bias
                    float remainingSpace = availableSpace - size;
                    position = startAnchor + startMargin + remainingSpace * bias;
                    break;
                case WRAP_CONTENT:
                default:
                    size = getPreferredSize(widget, horizontal);
                    remainingSpace = availableSpace - size;
                    position = startAnchor + startMargin + remainingSpace * bias;
                    break;
            }
        } else if (hasStart) {
            // Constrained on start side only
            position = startAnchor + startMargin;
            size = (sizeMode == UISizeMode.FIXED) ? fixedSize : getPreferredSize(widget, horizontal);
        } else if (hasEnd) {
            // Constrained on end side only
            size = (sizeMode == UISizeMode.FIXED) ? fixedSize : getPreferredSize(widget, horizontal);
            position = endAnchor - endMargin - size;
        } else {
            // Unconstrained — position at origin
            position = 0;
            size = (sizeMode == UISizeMode.FIXED) ? fixedSize : getPreferredSize(widget, horizontal);
        }

        if (horizontal) {
            rect.x = position;
            rect.width = size;
        } else {
            rect.y = position;
            rect.height = size;
        }
    }

    /**
     * Resolves the pixel position of a constraint anchor point.
     */
    private float resolveAnchorPosition(UIConstraint constraint,
                                         Map<String, LayoutRect> results,
                                         float parentSize, boolean horizontal) {
        if (constraint == null) return 0;

        if (constraint.isParentConstraint()) {
            return resolveParentAnchor(constraint.getTargetSide(), parentSize);
        }

        LayoutRect targetRect = results.get(constraint.getTargetName());
        if (targetRect == null) return 0;

        return resolveWidgetAnchor(constraint.getTargetSide(), targetRect, horizontal);
    }

    private float resolveParentAnchor(UIConstraintSide side, float parentSize) {
        switch (side) {
            case LEFT:
            case TOP:
                return 0;
            case RIGHT:
            case BOTTOM:
                return parentSize;
            default:
                return 0;
        }
    }

    private float resolveWidgetAnchor(UIConstraintSide side, LayoutRect rect, boolean horizontal) {
        switch (side) {
            case LEFT:
                return rect.x;
            case RIGHT:
                return rect.x + rect.width;
            case TOP:
                return rect.y;
            case BOTTOM:
                return rect.y + rect.height;
            default:
                return 0;
        }
    }

    // ========================================================================
    // Internal: preferred size for WRAP_CONTENT
    // ========================================================================

    /**
     * Returns the preferred size for a widget in WRAP_CONTENT mode.
     * For now, uses the fixed size as the preferred size.
     * When JME widgets are created, they can provide actual content measurements.
     */
    private float getPreferredSize(UIWidgetDef widget, boolean horizontal) {
        switch (widget.getType()) {
            case TEXT_VIEW:
                // Approximate: fontSize * character count for width, fontSize for height
                if (horizontal) {
                    String text = widget.getText();
                    return text != null ? text.length() * widget.getFontSize() * 0.6f : 50;
                } else {
                    return widget.getFontSize() * 1.4f;
                }
            case BUTTON:
                if (horizontal) {
                    String btnText = widget.getButtonText();
                    return btnText != null ? btnText.length() * widget.getFontSize() * 0.6f + 24 : 80;
                } else {
                    return widget.getFontSize() * 1.4f + 16;
                }
            case IMAGE:
                // Default preferred size for images; actual size comes from the texture
                return horizontal ? widget.getWidth() : widget.getHeight();
            case PANEL:
                // Panel wraps its children (fallback to fixed size)
                return horizontal ? widget.getWidth() : widget.getHeight();
            default:
                return horizontal ? widget.getWidth() : widget.getHeight();
        }
    }

    // ========================================================================
    // Internal: aspect ratio
    // ========================================================================

    private void resolveAspectRatio(UIWidgetDef widget, LayoutRect rect) {
        float ratio = widget.getAspectRatio(); // width / height
        if (ratio <= 0) return;

        // If width is MATCH_CONSTRAINT, compute width from height
        // If height is MATCH_CONSTRAINT, compute height from width
        // If both are MATCH_CONSTRAINT, use width as anchor
        if (widget.getWidthMode() == UISizeMode.MATCH_CONSTRAINT
                && widget.getHeightMode() != UISizeMode.MATCH_CONSTRAINT) {
            rect.width = rect.height * ratio;
        } else if (widget.getHeightMode() == UISizeMode.MATCH_CONSTRAINT) {
            rect.height = rect.width / ratio;
        }
    }

    // ========================================================================
    // Internal: chain resolution
    // ========================================================================

    /**
     * Resolves chains — groups of widgets linked together that distribute space.
     * A chain is defined by the head widget having a non-null chainStyle.
     * Chain members are discovered by following left→right or top→bottom constraint links.
     */
    private void resolveChains(List<UIWidgetDef> widgets,
                                Map<String, UIWidgetDef> widgetMap,
                                Map<String, LayoutRect> results,
                                float parentWidth, float parentHeight) {

        // Find horizontal chain heads
        for (UIWidgetDef w : widgets) {
            if (w.getHorizontalChainStyle() != null) {
                List<UIWidgetDef> chain = buildChain(w, widgetMap, true);
                if (chain.size() > 1) {
                    resolveChain(chain, results, parentWidth, true, w.getHorizontalChainStyle());
                }
            }
            if (w.getVerticalChainStyle() != null) {
                List<UIWidgetDef> chain = buildChain(w, widgetMap, false);
                if (chain.size() > 1) {
                    resolveChain(chain, results, parentHeight, false, w.getVerticalChainStyle());
                }
            }
        }
    }

    /**
     * Builds a chain starting from the head widget by following constraints.
     * A widget W2 is in the chain if W2 has a start-side constraint targeting W1's end-side
     * and W1 has an end-side constraint targeting W2's start-side (bidirectional link).
     */
    private List<UIWidgetDef> buildChain(UIWidgetDef head,
                                          Map<String, UIWidgetDef> widgetMap,
                                          boolean horizontal) {
        List<UIWidgetDef> chain = new ArrayList<>();
        chain.add(head);

        UIConstraintSide endSide = horizontal ? UIConstraintSide.RIGHT : UIConstraintSide.BOTTOM;
        UIConstraintSide startSide = horizontal ? UIConstraintSide.LEFT : UIConstraintSide.TOP;

        UIWidgetDef current = head;
        Set<String> visited = new HashSet<>();
        visited.add(current.getName());

        while (true) {
            UIConstraint endConstraint = current.getConstraintForSide(endSide);
            if (endConstraint == null || endConstraint.isParentConstraint()) break;

            String nextName = endConstraint.getTargetName();
            if (visited.contains(nextName)) break;

            UIWidgetDef next = widgetMap.get(nextName);
            if (next == null) break;

            // Verify bidirectional link
            UIConstraint nextStartConstraint = next.getConstraintForSide(startSide);
            if (nextStartConstraint == null || !nextStartConstraint.getTargetName().equals(current.getName())) {
                break;
            }

            chain.add(next);
            visited.add(nextName);
            current = next;
        }

        return chain;
    }

    private void resolveChain(List<UIWidgetDef> chain,
                               Map<String, LayoutRect> results,
                               float parentSize, boolean horizontal,
                               UIChainStyle style) {
        // Calculate total size of chain elements
        float totalWidgetSize = 0;
        float totalWeight = 0;
        List<LayoutRect> rects = new ArrayList<>();

        for (UIWidgetDef w : chain) {
            LayoutRect rect = results.get(w.getName());
            rects.add(rect);
            float size = horizontal ? rect.width : rect.height;
            totalWidgetSize += size;
            totalWeight += horizontal ? w.getHorizontalWeight() : w.getVerticalWeight();
        }

        float availableSpace = parentSize - totalWidgetSize;
        int n = chain.size();

        switch (style) {
            case SPREAD: {
                // Equal gaps, including before first and after last
                float gap = availableSpace / (n + 1);
                float pos = gap;
                for (LayoutRect rect : rects) {
                    if (horizontal) {
                        rect.x = pos;
                        pos += rect.width + gap;
                    } else {
                        rect.y = pos;
                        pos += rect.height + gap;
                    }
                }
                break;
            }
            case SPREAD_INSIDE: {
                if (n == 1) {
                    // Single widget — center it
                    LayoutRect rect = rects.get(0);
                    if (horizontal) rect.x = (parentSize - rect.width) / 2;
                    else rect.y = (parentSize - rect.height) / 2;
                } else {
                    // Equal gaps between widgets, first and last touch edges
                    float gap = availableSpace / (n - 1);
                    float pos = 0;
                    for (LayoutRect rect : rects) {
                        if (horizontal) {
                            rect.x = pos;
                            pos += rect.width + gap;
                        } else {
                            rect.y = pos;
                            pos += rect.height + gap;
                        }
                    }
                }
                break;
            }
            case PACKED: {
                // All widgets packed together, positioned using the head's bias
                float bias = horizontal
                    ? chain.get(0).getHorizontalBias()
                    : chain.get(0).getVerticalBias();
                float startPos = availableSpace * bias;
                float pos = startPos;
                for (LayoutRect rect : rects) {
                    if (horizontal) {
                        rect.x = pos;
                        pos += rect.width;
                    } else {
                        rect.y = pos;
                        pos += rect.height;
                    }
                }
                break;
            }
        }
    }
}

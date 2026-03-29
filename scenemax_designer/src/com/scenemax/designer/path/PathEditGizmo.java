package com.scenemax.designer.path;

import com.jme3.math.Plane;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.scenemax.designer.DesignerEntity;

/**
 * Handles drag-editing of control points and tangent handles on a PATH entity.
 * Uses screen-space distance picking (similar to GizmoManager).
 * <p>
 * When a PATH entity is selected, this gizmo activates and allows:
 * - Dragging control points (on the XZ ground plane by default)
 * - Dragging tangent handles (mirror/break)
 * - Alt+drag to break tangent symmetry
 */
public class PathEditGizmo {

    public interface EditCallback {
        void onEditFinished(DesignerEntity entity);
    }

    private static final float PICK_THRESHOLD_PX = 18f;

    private DesignerEntity attachedEntity;
    private PathVisual pathVisual;
    private EditCallback editCallback;

    // Drag state
    private boolean dragging = false;
    private DragTarget dragTarget = DragTarget.NONE;
    private int dragPointIndex = -1;
    private Plane dragPlane;
    private Vector3f dragOffset;
    private boolean altHeld = false;
    private boolean shiftHeld = false;

    private enum DragTarget {
        NONE,
        CONTROL_POINT,
        TANGENT_IN,
        TANGENT_OUT
    }

    public void setEditCallback(EditCallback callback) {
        this.editCallback = callback;
    }

    public void attach(DesignerEntity entity, PathVisual visual) {
        this.attachedEntity = entity;
        this.pathVisual = visual;
        if (visual != null) {
            visual.setShowHandles(true);
        }
    }

    public void detach() {
        if (pathVisual != null) {
            pathVisual.setShowHandles(false);
        }
        attachedEntity = null;
        pathVisual = null;
        dragging = false;
    }

    public boolean isAttached() {
        return attachedEntity != null;
    }

    public boolean isDragging() {
        return dragging;
    }

    public void setAltHeld(boolean altHeld) {
        this.altHeld = altHeld;
    }

    public void setShiftHeld(boolean shiftHeld) {
        this.shiftHeld = shiftHeld;
    }

    /**
     * Attempts to start a drag on a control point or tangent handle.
     * Returns true if a drag was started.
     */
    public boolean tryStartDrag(Camera cam, Vector2f screenPos) {
        if (attachedEntity == null || attachedEntity.getBezierPath() == null) return false;

        BezierPath path = attachedEntity.getBezierPath();

        // Check tangent handles first (smaller targets, should have priority)
        for (int i = 0; i < path.getPointCount(); i++) {
            BezierControlPoint cp = path.getPoint(i);

            // Tangent out handle
            Vector3f handleOutScreen = cam.getScreenCoordinates(cp.getTangentOutWorld());
            float distOut = screenPos.distance(new Vector2f(handleOutScreen.x, handleOutScreen.y));
            if (distOut < PICK_THRESHOLD_PX) {
                startDrag(cam, screenPos, i, DragTarget.TANGENT_OUT, cp.getTangentOutWorld());
                return true;
            }

            // Tangent in handle
            Vector3f handleInScreen = cam.getScreenCoordinates(cp.getTangentInWorld());
            float distIn = screenPos.distance(new Vector2f(handleInScreen.x, handleInScreen.y));
            if (distIn < PICK_THRESHOLD_PX) {
                startDrag(cam, screenPos, i, DragTarget.TANGENT_IN, cp.getTangentInWorld());
                return true;
            }
        }

        // Check control points
        for (int i = 0; i < path.getPointCount(); i++) {
            BezierControlPoint cp = path.getPoint(i);
            Vector3f cpScreen = cam.getScreenCoordinates(cp.getPosition());
            float dist = screenPos.distance(new Vector2f(cpScreen.x, cpScreen.y));
            if (dist < PICK_THRESHOLD_PX) {
                startDrag(cam, screenPos, i, DragTarget.CONTROL_POINT, cp.getPosition());
                return true;
            }
        }

        return false;
    }

    private boolean dragVertical = false;
    private Vector3f dragStartPos;

    private void startDrag(Camera cam, Vector2f screenPos, int index, DragTarget target, Vector3f worldPos) {
        dragging = true;
        dragPointIndex = index;
        dragTarget = target;
        dragStartPos = worldPos.clone();

        if (shiftHeld) {
            // Shift+drag: vertical (Y-axis) movement
            // Use a plane facing the camera that passes through the point
            dragVertical = true;
            Vector3f camDir = cam.getDirection().clone();
            camDir.y = 0;
            camDir.normalizeLocal();
            // Create a vertical plane perpendicular to the camera's horizontal direction
            dragPlane = new Plane(camDir, camDir.dot(worldPos));
        } else {
            // Normal drag: XZ ground plane at the point's Y position
            dragVertical = false;
            dragPlane = new Plane(Vector3f.UNIT_Y, worldPos.y);
        }

        // Compute offset from ray-plane hit to actual point (so dragging feels smooth)
        Vector3f hitPos = raycastPlane(cam, screenPos, dragPlane);
        if (hitPos != null) {
            dragOffset = worldPos.subtract(hitPos);
        } else {
            dragOffset = Vector3f.ZERO.clone();
        }
    }

    /**
     * Updates the drag in progress.
     */
    public void updateDrag(Camera cam, Vector2f screenPos) {
        if (!dragging || attachedEntity == null) return;

        BezierPath path = attachedEntity.getBezierPath();
        if (path == null || dragPointIndex < 0 || dragPointIndex >= path.getPointCount()) return;

        Vector3f hitPos = raycastPlane(cam, screenPos, dragPlane);
        if (hitPos == null) return;

        Vector3f newPos = hitPos.add(dragOffset);

        // If vertical drag, only change Y, keep original X/Z
        if (dragVertical) {
            newPos.x = dragStartPos.x;
            newPos.z = dragStartPos.z;
        }

        BezierControlPoint cp = path.getPoint(dragPointIndex);

        switch (dragTarget) {
            case CONTROL_POINT:
                // Move the control point; tangent handles move with it
                cp.setPosition(newPos);
                if (dragVertical) {
                    dragStartPos.y = newPos.y; // track for continued dragging
                }
                break;

            case TANGENT_OUT:
                // Set tangent-out as offset from control point position
                Vector3f outOffset = newPos.subtract(cp.getPosition());
                if (altHeld) {
                    // Alt+drag: break tangent symmetry
                    cp.setTangentBroken(true);
                    cp.setTangentOut(outOffset);
                } else {
                    cp.setTangentOutMirrored(outOffset);
                }
                break;

            case TANGENT_IN:
                // Set tangent-in as offset from control point position
                Vector3f inOffset = newPos.subtract(cp.getPosition());
                if (altHeld) {
                    cp.setTangentBroken(true);
                    cp.setTangentIn(inOffset);
                } else {
                    cp.setTangentInMirrored(inOffset);
                }
                break;
        }

        // Rebuild visual
        if (pathVisual != null) {
            pathVisual.rebuild(path);
        }
    }

    /**
     * Ends the current drag operation.
     */
    public void endDrag() {
        if (!dragging) return;

        dragging = false;
        dragTarget = DragTarget.NONE;
        dragPointIndex = -1;
        dragPlane = null;
        dragOffset = null;

        if (editCallback != null && attachedEntity != null) {
            editCallback.onEditFinished(attachedEntity);
        }
    }

    /**
     * Attempts to insert a new control point on the curve segment closest
     * to the screen position. Returns true if a point was inserted.
     */
    public boolean tryInsertPoint(Camera cam, Vector2f screenPos) {
        if (attachedEntity == null || attachedEntity.getBezierPath() == null) return false;

        BezierPath path = attachedEntity.getBezierPath();
        if (path.getSegmentCount() == 0) return false;

        float bestDist = Float.MAX_VALUE;
        int bestSeg = -1;
        float bestT = 0;

        // Find closest point on curve
        for (int seg = 0; seg < path.getSegmentCount(); seg++) {
            for (int s = 0; s <= 20; s++) {
                float t = (float) s / 20;
                Vector3f worldPt = path.evaluate(seg, t);
                Vector3f screenPt = cam.getScreenCoordinates(worldPt);
                float dist = screenPos.distance(new Vector2f(screenPt.x, screenPt.y));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestSeg = seg;
                    bestT = t;
                }
            }
        }

        if (bestDist < PICK_THRESHOLD_PX * 2 && bestSeg >= 0) {
            // Insert a new point at the curve position
            Vector3f newPos = path.evaluate(bestSeg, bestT);
            BezierControlPoint newCp = new BezierControlPoint(newPos);
            path.insertPoint(bestSeg + 1, newCp);
            path.autoSmoothAll();

            if (pathVisual != null) {
                pathVisual.rebuild(path);
            }

            if (editCallback != null) {
                editCallback.onEditFinished(attachedEntity);
            }
            return true;
        }

        return false;
    }

    /**
     * Removes the control point closest to the screen position.
     * Returns true if a point was removed.
     */
    public boolean tryRemovePoint(Camera cam, Vector2f screenPos) {
        if (attachedEntity == null || attachedEntity.getBezierPath() == null) return false;

        BezierPath path = attachedEntity.getBezierPath();
        if (path.getPointCount() <= 2) return false; // minimum 2 points

        for (int i = 0; i < path.getPointCount(); i++) {
            Vector3f cpScreen = cam.getScreenCoordinates(path.getPoint(i).getPosition());
            float dist = screenPos.distance(new Vector2f(cpScreen.x, cpScreen.y));
            if (dist < PICK_THRESHOLD_PX) {
                path.removePoint(i);
                path.autoSmoothAll();

                if (pathVisual != null) {
                    pathVisual.rebuild(path);
                }

                if (editCallback != null) {
                    editCallback.onEditFinished(attachedEntity);
                }
                return true;
            }
        }
        return false;
    }

    private Vector3f raycastPlane(Camera cam, Vector2f screenPos, Plane plane) {
        Vector3f near = cam.getWorldCoordinates(screenPos, 0f);
        Vector3f far = cam.getWorldCoordinates(screenPos, 1f);
        Vector3f dir = far.subtract(near).normalizeLocal();

        float denom = plane.getNormal().dot(dir);
        if (Math.abs(denom) < 1e-6f) return null;

        float t = (plane.getConstant() - plane.getNormal().dot(near)) / denom;
        if (t < 0) return null;

        return near.add(dir.mult(t));
    }
}

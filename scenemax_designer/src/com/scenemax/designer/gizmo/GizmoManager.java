package com.scenemax.designer.gizmo;

import com.jme3.math.FastMath;
import com.jme3.math.Plane;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;
import com.scenemax.designer.selection.SelectionManager;

/**
 * Manages gizmo display and drag interaction for the selected entity.
 * Supports both TRANSLATE and ROTATE modes.
 *
 * Picking uses screen-space distance to axis arrows or ring circles.
 *
 * Translate drag: plane-based projection onto a fixed axis.
 * Rotate drag: angle computation on a plane perpendicular to the rotation axis.
 */
public class GizmoManager implements SelectionManager.SelectionListener {

    /**
     * Callback to notify when a drag operation finishes so the UI can update.
     */
    public interface DragEndCallback {
        void onDragEnd(DesignerEntity entity);
    }

    private final Node rootNode;
    private TranslateGizmo translateGizmo;
    private RotateGizmo rotateGizmo;
    private GizmoMode mode = GizmoMode.TRANSLATE;
    private DesignerEntity attachedEntity;
    private DragEndCallback dragEndCallback;

    // Drag state — all reference values captured at drag start and stay fixed
    private boolean dragging = false;
    private String dragAxis = null;
    private Vector3f dragAxisDir = null;

    // Translate drag state
    private Plane dragPlane = null;
    private Vector3f dragOrigin = null;
    private float dragStartT;
    private Vector3f entityStartPos;

    // Rotate drag state
    private Plane rotatePlane = null;
    private Vector3f rotateCenter = null;
    private float rotateStartAngle;
    private Quaternion entityStartRotation;

    /** Screen-space pixel threshold for picking */
    private static final float PICK_THRESHOLD_PX = 18f;

    public GizmoManager(Node rootNode, TranslateGizmo translateGizmo, RotateGizmo rotateGizmo) {
        this.rootNode = rootNode;
        this.translateGizmo = translateGizmo;
        this.rotateGizmo = rotateGizmo;
        translateGizmo.setCullHint(Node.CullHint.Always);
        rotateGizmo.setCullHint(Node.CullHint.Always);
    }

    public void setDragEndCallback(DragEndCallback callback) {
        this.dragEndCallback = callback;
    }

    public GizmoMode getMode() {
        return mode;
    }

    public void setMode(GizmoMode mode) {
        this.mode = mode;
        updateGizmoVisibility();
    }

    public boolean isDragging() {
        return dragging;
    }

    @Override
    public void onSelectionChanged(DesignerEntity newSelection) {
        attachedEntity = newSelection;
        // PATH entities use their own edit gizmo; hide standard gizmos
        if (newSelection != null && newSelection.getType() == DesignerEntityType.PATH) {
            translateGizmo.setCullHint(Node.CullHint.Always);
            rotateGizmo.setCullHint(Node.CullHint.Always);
            return;
        }
        if (newSelection != null && newSelection.getSceneNode() != null) {
            updateGizmoPosition();
            updateGizmoVisibility();
        } else {
            translateGizmo.setCullHint(Node.CullHint.Always);
            rotateGizmo.setCullHint(Node.CullHint.Always);
        }
    }

    private void updateGizmoVisibility() {
        if (attachedEntity == null || attachedEntity.getSceneNode() == null) {
            translateGizmo.setCullHint(Node.CullHint.Always);
            rotateGizmo.setCullHint(Node.CullHint.Always);
            return;
        }
        if (mode == GizmoMode.TRANSLATE) {
            translateGizmo.setCullHint(Node.CullHint.Never);
            rotateGizmo.setCullHint(Node.CullHint.Always);
        } else if (mode == GizmoMode.ROTATE) {
            translateGizmo.setCullHint(Node.CullHint.Always);
            rotateGizmo.setCullHint(Node.CullHint.Never);
        }
    }

    public void updateGizmoPosition() {
        if (attachedEntity != null && attachedEntity.getSceneNode() != null) {
            Vector3f worldPos = attachedEntity.getSceneNode().getWorldTranslation();
            translateGizmo.setLocalTranslation(worldPos);
            rotateGizmo.setLocalTranslation(worldPos);
        }
    }

    // =====================================================================
    //  PICKING — tries to start a drag based on the current gizmo mode
    // =====================================================================

    public boolean tryStartDrag(Camera cam, Vector2f screenPos) {
        if (attachedEntity == null) return false;

        if (mode == GizmoMode.TRANSLATE) {
            return tryStartTranslateDrag(cam, screenPos);
        } else if (mode == GizmoMode.ROTATE) {
            return tryStartRotateDrag(cam, screenPos);
        }
        return false;
    }

    // =====================================================================
    //  TRANSLATE DRAG
    // =====================================================================

    private boolean tryStartTranslateDrag(Camera cam, Vector2f screenPos) {
        Vector3f origin3D = translateGizmo.getWorldTranslation();
        Vector3f originScreen = cam.getScreenCoordinates(origin3D);

        String bestAxis = null;
        float bestDist = Float.MAX_VALUE;

        for (String axis : new String[]{"X", "Y", "Z"}) {
            Vector3f endpoint3D = translateGizmo.getAxisEndpoint(axis);
            Vector3f endScreen = cam.getScreenCoordinates(endpoint3D);

            float dist = distPointToSegment2D(
                    screenPos.x, screenPos.y,
                    originScreen.x, originScreen.y,
                    endScreen.x, endScreen.y);

            if (dist < bestDist) {
                bestDist = dist;
                bestAxis = axis;
            }
        }

        if (bestAxis != null && bestDist < PICK_THRESHOLD_PX) {
            dragging = true;
            dragAxis = bestAxis;
            dragAxisDir = getAxisDirection(bestAxis);
            entityStartPos = attachedEntity.getSceneNode().getLocalTranslation().clone();

            dragOrigin = origin3D.clone();
            dragPlane = buildTranslateDragPlane(dragOrigin, dragAxisDir, cam);
            dragStartT = computeTranslateAxisT(cam, screenPos);

            return true;
        }
        return false;
    }

    private void updateTranslateDrag(Camera cam, Vector2f screenPos) {
        float currentT = computeTranslateAxisT(cam, screenPos);
        float deltaT = currentT - dragStartT;
        Vector3f newPos = entityStartPos.add(dragAxisDir.mult(deltaT));
        attachedEntity.getSceneNode().setLocalTranslation(newPos);
        updateGizmoPosition();
    }

    private Plane buildTranslateDragPlane(Vector3f axisOrigin, Vector3f axisDir, Camera cam) {
        Vector3f toGizmo = axisOrigin.subtract(cam.getLocation());
        Vector3f planeNormal = toGizmo.subtract(axisDir.mult(toGizmo.dot(axisDir)));

        if (planeNormal.lengthSquared() < 0.0001f) {
            planeNormal = cam.getUp().subtract(axisDir.mult(cam.getUp().dot(axisDir)));
        }
        planeNormal.normalizeLocal();

        Plane plane = new Plane();
        plane.setOriginNormal(axisOrigin, planeNormal);
        return plane;
    }

    private float computeTranslateAxisT(Camera cam, Vector2f screenPos) {
        Vector3f worldNear = cam.getWorldCoordinates(screenPos, 0f).clone();
        Vector3f worldFar = cam.getWorldCoordinates(screenPos, 1f).clone();
        Vector3f rayDir = worldFar.subtract(worldNear).normalizeLocal();

        float denom = dragPlane.getNormal().dot(rayDir);
        if (Math.abs(denom) < 1e-8f) {
            return dragStartT;
        }

        float dist = (dragPlane.getConstant() - dragPlane.getNormal().dot(worldNear)) / denom;
        Vector3f hitPoint = worldNear.add(rayDir.mult(dist));

        Vector3f offset = hitPoint.subtract(dragOrigin);
        return offset.dot(dragAxisDir);
    }

    // =====================================================================
    //  ROTATE DRAG
    // =====================================================================

    private boolean tryStartRotateDrag(Camera cam, Vector2f screenPos) {
        // Sample points on each ring and check screen-space distance
        String bestAxis = null;
        float bestDist = Float.MAX_VALUE;

        for (String axis : new String[]{"X", "Y", "Z"}) {
            Vector3f[] ringPoints = rotateGizmo.getRingPoints(axis, 24);

            // Find minimum distance from click to any ring segment
            for (int i = 0; i < ringPoints.length; i++) {
                int j = (i + 1) % ringPoints.length;
                Vector3f screenA = cam.getScreenCoordinates(ringPoints[i]);
                Vector3f screenB = cam.getScreenCoordinates(ringPoints[j]);

                float dist = distPointToSegment2D(
                        screenPos.x, screenPos.y,
                        screenA.x, screenA.y,
                        screenB.x, screenB.y);

                if (dist < bestDist) {
                    bestDist = dist;
                    bestAxis = axis;
                }
            }
        }

        if (bestAxis != null && bestDist < PICK_THRESHOLD_PX) {
            dragging = true;
            dragAxis = bestAxis;
            dragAxisDir = getAxisDirection(bestAxis);
            entityStartRotation = attachedEntity.getSceneNode().getLocalRotation().clone();

            // Build rotation plane: perpendicular to the rotation axis, through entity center
            rotateCenter = rotateGizmo.getWorldTranslation().clone();
            rotatePlane = new Plane();
            rotatePlane.setOriginNormal(rotateCenter, dragAxisDir);

            // Compute initial angle
            rotateStartAngle = computeRotateAngle(cam, screenPos);

            return true;
        }
        return false;
    }

    private void updateRotateDrag(Camera cam, Vector2f screenPos) {
        float currentAngle = computeRotateAngle(cam, screenPos);
        float deltaAngle = currentAngle - rotateStartAngle;

        // Build incremental rotation around the axis
        Quaternion deltaRot = new Quaternion();
        deltaRot.fromAngleAxis(deltaAngle, dragAxisDir);

        // Apply: newRotation = deltaRot * startRotation
        Quaternion newRot = deltaRot.mult(entityStartRotation);
        attachedEntity.getSceneNode().setLocalRotation(newRot);
    }

    /**
     * Computes the angle of the mouse ray intersection on the rotation plane,
     * measured from the rotation center.
     */
    private float computeRotateAngle(Camera cam, Vector2f screenPos) {
        Vector3f worldNear = cam.getWorldCoordinates(screenPos, 0f).clone();
        Vector3f worldFar = cam.getWorldCoordinates(screenPos, 1f).clone();
        Vector3f rayDir = worldFar.subtract(worldNear).normalizeLocal();

        float denom = rotatePlane.getNormal().dot(rayDir);
        if (Math.abs(denom) < 1e-8f) {
            return rotateStartAngle;
        }

        float dist = (rotatePlane.getConstant() - rotatePlane.getNormal().dot(worldNear)) / denom;
        Vector3f hitPoint = worldNear.add(rayDir.mult(dist));

        // Vector from center to hit point, in the rotation plane
        Vector3f radial = hitPoint.subtract(rotateCenter);

        // Build two orthogonal axes in the rotation plane to compute angle
        // Axis1: any direction perpendicular to dragAxisDir
        Vector3f axis1, axis2;
        if (Math.abs(dragAxisDir.dot(Vector3f.UNIT_Y)) < 0.9f) {
            axis1 = dragAxisDir.cross(Vector3f.UNIT_Y).normalizeLocal();
        } else {
            axis1 = dragAxisDir.cross(Vector3f.UNIT_X).normalizeLocal();
        }
        axis2 = dragAxisDir.cross(axis1).normalizeLocal();

        float x = radial.dot(axis1);
        float y = radial.dot(axis2);
        return FastMath.atan2(y, x);
    }

    // =====================================================================
    //  UPDATE & END DRAG
    // =====================================================================

    public void updateDrag(Camera cam, Vector2f screenPos) {
        if (!dragging || attachedEntity == null) return;

        if (mode == GizmoMode.TRANSLATE) {
            updateTranslateDrag(cam, screenPos);
        } else if (mode == GizmoMode.ROTATE) {
            updateRotateDrag(cam, screenPos);
        }
    }

    public void endDrag() {
        DesignerEntity entity = attachedEntity;
        dragging = false;
        dragAxis = null;
        dragAxisDir = null;
        dragPlane = null;
        dragOrigin = null;
        entityStartPos = null;
        rotatePlane = null;
        rotateCenter = null;
        entityStartRotation = null;

        // Notify that drag finished so properties panel can update
        if (dragEndCallback != null && entity != null) {
            dragEndCallback.onDragEnd(entity);
        }
    }

    // =====================================================================
    //  UTILITIES
    // =====================================================================

    private float distPointToSegment2D(float px, float py,
                                        float x1, float y1,
                                        float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lenSq = dx * dx + dy * dy;

        if (lenSq < 0.0001f) {
            float ex = px - x1;
            float ey = py - y1;
            return (float) Math.sqrt(ex * ex + ey * ey);
        }

        float t = ((px - x1) * dx + (py - y1) * dy) / lenSq;
        t = Math.max(0f, Math.min(1f, t));

        float closestX = x1 + t * dx;
        float closestY = y1 + t * dy;
        float ex = px - closestX;
        float ey = py - closestY;
        return (float) Math.sqrt(ex * ex + ey * ey);
    }

    private Vector3f getAxisDirection(String axis) {
        switch (axis) {
            case "X": return Vector3f.UNIT_X.clone();
            case "Y": return Vector3f.UNIT_Y.clone();
            case "Z": return Vector3f.UNIT_Z.clone();
            default: return Vector3f.UNIT_X.clone();
        }
    }

    public void scaleGizmoToCamera(Camera cam) {
        if (attachedEntity == null || attachedEntity.getSceneNode() == null) return;
        float dist = cam.getLocation().distance(translateGizmo.getWorldTranslation());
        float scale = dist * 0.15f;
        scale = Math.max(0.5f, Math.min(scale, 5f));
        translateGizmo.setLocalScale(scale);
        rotateGizmo.setLocalScale(scale);
    }
}

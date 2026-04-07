package com.scenemax.designer.selection;

import com.jme3.collision.CollisionResult;
import com.jme3.collision.CollisionResults;
import com.jme3.math.Ray;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Node;
import com.scenemax.designer.DesignerEntity;
import com.scenemax.designer.DesignerEntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages object selection via ray-cast picking.
 */
public class SelectionManager {

    private DesignerEntity selected;
    private final List<SelectionListener> listeners = new ArrayList<>();

    public interface SelectionListener {
        void onSelectionChanged(DesignerEntity newSelection);
    }

    public void addListener(SelectionListener listener) {
        listeners.add(listener);
    }

    public DesignerEntity getSelected() {
        return selected;
    }

    public boolean isSelected(DesignerEntity entity) {
        return selected != null && selected.equals(entity);
    }

    public void select(DesignerEntity entity) {
        if (this.selected == entity) return;
        this.selected = entity;
        for (SelectionListener l : listeners) {
            l.onSelectionChanged(entity);
        }
    }

    public void deselect() {
        select(null);
    }

    /**
     * Performs a ray-cast pick from screen coordinates against the given entities.
     * Returns the picked entity, or null if nothing was hit.
     */
    public DesignerEntity pick(Camera cam, Vector2f screenPos, List<DesignerEntity> entities) {
        Vector3f worldCoords = cam.getWorldCoordinates(screenPos, 0f).clone();
        Vector3f direction = cam.getWorldCoordinates(screenPos, 1f).subtractLocal(worldCoords).normalizeLocal();

        Ray ray = new Ray(worldCoords, direction);

        float[] closestDist = { Float.MAX_VALUE };
        DesignerEntity[] closest = { null };

        pickRecursive(ray, entities, closest, closestDist);

        return closest[0];
    }

    private void pickRecursive(Ray ray, List<DesignerEntity> entities,
                               DesignerEntity[] closest, float[] closestDist) {
        for (DesignerEntity entity : entities) {
            // Recurse into section children
            if (entity.getType() == DesignerEntityType.SECTION
                    || entity.getType() == DesignerEntityType.CINEMATIC_RIG) {
                pickRecursive(ray, entity.getChildren(), closest, closestDist);
                continue;
            }

            Node node = entity.getSceneNode();
            if (node == null) continue;

            CollisionResults results = new CollisionResults();
            node.collideWith(ray, results);

            if (results.size() > 0) {
                CollisionResult hit = results.getClosestCollision();
                if (hit.getDistance() < closestDist[0]) {
                    closestDist[0] = hit.getDistance();
                    closest[0] = entity;
                }
            }
        }
    }
}

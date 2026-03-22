package com.scenemax.designer.gizmo;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.control.BillboardControl;
import com.jme3.scene.debug.Arrow;
import com.jme3.scene.shape.Sphere;

/**
 * Orientation gizmo rendered in a separate ViewPort in the top-right corner.
 * Shows 3 colored axis arrows with labeled spheres. Clicking an axis endpoint
 * fires a callback to switch the main camera to a preset view.
 */
public class ViewCubeGizmo {

    public interface ViewPresetCallback {
        void onViewPresetSelected(float targetYaw, float targetPitch);
    }

    private static final int VIEWPORT_SIZE_PX = 120;
    private static final float CAM_DISTANCE = 5f;
    private static final float AXIS_LENGTH = 1.2f;
    private static final float SPHERE_RADIUS = 0.18f;
    private static final float PICK_THRESHOLD_PX = 20f;
    private static final float LABEL_SIZE = 0.35f;
    private static final float LABEL_OFFSET = 0.35f;
    private static final int MARGIN_PX = 10;

    private Node sceneNode;
    private Camera viewCubeCam;
    private ViewPort viewPort;
    private ViewPresetCallback callback;

    // Axis tip positions in the viewcube scene (local space)
    private final Vector3f tipXPos = new Vector3f(AXIS_LENGTH, 0, 0);
    private final Vector3f tipYPos = new Vector3f(0, AXIS_LENGTH, 0);
    private final Vector3f tipZPos = new Vector3f(0, 0, AXIS_LENGTH);
    private final Vector3f tipXNeg = new Vector3f(-AXIS_LENGTH, 0, 0);
    private final Vector3f tipYNeg = new Vector3f(0, -AXIS_LENGTH, 0);
    private final Vector3f tipZNeg = new Vector3f(0, 0, -AXIS_LENGTH);

    // All clickable tips and their preset yaw/pitch values
    // Order: +X, +Y, +Z, -X, -Y, -Z, center
    private final Vector3f[] clickTargets;
    private final float[][] presets;

    public ViewCubeGizmo() {
        clickTargets = new Vector3f[]{
                tipXPos, tipYPos, tipZPos,
                tipXNeg, tipYNeg, tipZNeg,
                Vector3f.ZERO
        };
        presets = new float[][]{
                {(float) (Math.PI / 2), 0},                         // +X = Right
                {Float.NaN, (float) Math.toRadians(89)},             // +Y = Top (keep yaw)
                {0, 0},                                              // +Z = Front
                {(float) (-Math.PI / 2), 0},                        // -X = Left
                {Float.NaN, (float) Math.toRadians(-89)},            // -Y = Bottom (keep yaw)
                {(float) Math.PI, 0},                                // -Z = Back
                {(float) Math.toRadians(45), (float) Math.toRadians(30)}  // Center = Default
        };
    }

    public void setCallback(ViewPresetCallback callback) {
        this.callback = callback;
    }

    /**
     * Initializes the viewcube geometry, camera, and viewport.
     * Must be called from the JME3 render thread (e.g., simpleInitApp).
     */
    public void init(AssetManager assetManager, RenderManager renderManager,
                     Camera mainCam) {
        sceneNode = new Node("ViewCubeScene");

        // Positive axis arrows (full color)
        sceneNode.attachChild(createArrow(assetManager, "X+",
                new Vector3f(AXIS_LENGTH, 0, 0), ColorRGBA.Red));
        sceneNode.attachChild(createArrow(assetManager, "Y+",
                new Vector3f(0, AXIS_LENGTH, 0), ColorRGBA.Green));
        sceneNode.attachChild(createArrow(assetManager, "Z+",
                new Vector3f(0, 0, AXIS_LENGTH), ColorRGBA.Blue));

        // Negative axis arrows (dimmer)
        sceneNode.attachChild(createArrow(assetManager, "X-",
                new Vector3f(-AXIS_LENGTH, 0, 0), new ColorRGBA(0.5f, 0.15f, 0.15f, 1f)));
        sceneNode.attachChild(createArrow(assetManager, "Y-",
                new Vector3f(0, -AXIS_LENGTH, 0), new ColorRGBA(0.15f, 0.5f, 0.15f, 1f)));
        sceneNode.attachChild(createArrow(assetManager, "Z-",
                new Vector3f(0, 0, -AXIS_LENGTH), new ColorRGBA(0.15f, 0.15f, 0.5f, 1f)));

        // Spheres at positive tips
        sceneNode.attachChild(createSphere(assetManager, "SphX+", tipXPos, ColorRGBA.Red, SPHERE_RADIUS));
        sceneNode.attachChild(createSphere(assetManager, "SphY+", tipYPos, ColorRGBA.Green, SPHERE_RADIUS));
        sceneNode.attachChild(createSphere(assetManager, "SphZ+", tipZPos, ColorRGBA.Blue, SPHERE_RADIUS));

        // Spheres at negative tips (smaller, dimmer)
        sceneNode.attachChild(createSphere(assetManager, "SphX-", tipXNeg,
                new ColorRGBA(0.5f, 0.15f, 0.15f, 1f), SPHERE_RADIUS * 0.7f));
        sceneNode.attachChild(createSphere(assetManager, "SphY-", tipYNeg,
                new ColorRGBA(0.15f, 0.5f, 0.15f, 1f), SPHERE_RADIUS * 0.7f));
        sceneNode.attachChild(createSphere(assetManager, "SphZ-", tipZNeg,
                new ColorRGBA(0.15f, 0.15f, 0.5f, 1f), SPHERE_RADIUS * 0.7f));

        // Center sphere (default view)
        sceneNode.attachChild(createSphere(assetManager, "Center", Vector3f.ZERO,
                new ColorRGBA(0.4f, 0.4f, 0.4f, 1f), 0.1f));

        // Text labels at positive tips with billboard control
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        sceneNode.attachChild(createLabel(font, "X", tipXPos, ColorRGBA.White));
        sceneNode.attachChild(createLabel(font, "Y", tipYPos, ColorRGBA.White));
        sceneNode.attachChild(createLabel(font, "Z", tipZPos, ColorRGBA.White));

        // Camera for the viewcube viewport
        int w = mainCam.getWidth();
        int h = mainCam.getHeight();
        viewCubeCam = new Camera(w, h);
        viewCubeCam.setFrustumPerspective(45f, 1f, 0.1f, 100f);
        updateViewportBounds(w, h);

        // Create post-view so it renders on top of the main scene
        viewPort = renderManager.createPostView("ViewCubeVP", viewCubeCam);
        viewPort.setClearFlags(false, true, false);
        viewPort.attachScene(sceneNode);

        sceneNode.updateGeometricState();
    }

    /**
     * Syncs the viewcube camera rotation with the main camera using the
     * same spherical coordinate formula as DesignerApp.updateOrbitCamera().
     */
    public void syncCamera(float mainYaw, float mainPitch) {
        if (viewCubeCam == null) return;

        float x = CAM_DISTANCE * (float) (Math.cos(mainPitch) * Math.sin(mainYaw));
        float y = CAM_DISTANCE * (float) Math.sin(mainPitch);
        float z = CAM_DISTANCE * (float) (Math.cos(mainPitch) * Math.cos(mainYaw));

        viewCubeCam.setLocation(new Vector3f(x, y, z));
        viewCubeCam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);

        sceneNode.updateLogicalState(0);
        sceneNode.updateGeometricState();
    }

    /**
     * Checks if a screen click hits a viewcube axis endpoint and triggers
     * the preset callback if so.
     *
     * @return true if the click was consumed by the viewcube
     */
    public boolean tryClick(Vector2f screenPos, int screenWidth, int screenHeight) {
        if (viewCubeCam == null || callback == null) return false;

        // Check if click is within the viewcube viewport region
        float vpLeft = viewCubeCam.getViewPortLeft() * screenWidth;
        float vpRight = viewCubeCam.getViewPortRight() * screenWidth;
        float vpBottom = viewCubeCam.getViewPortBottom() * screenHeight;
        float vpTop = viewCubeCam.getViewPortTop() * screenHeight;

        if (screenPos.x < vpLeft || screenPos.x > vpRight ||
                screenPos.y < vpBottom || screenPos.y > vpTop) {
            return false;
        }

        // Project each clickable target through the viewcube camera
        // and find the closest one to the click position
        int bestIndex = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < clickTargets.length; i++) {
            Vector3f screenTip = viewCubeCam.getScreenCoordinates(clickTargets[i]);
            float dx = screenPos.x - screenTip.x;
            float dy = screenPos.y - screenTip.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
            }
        }

        if (bestIndex >= 0 && bestDist < PICK_THRESHOLD_PX) {
            callback.onViewPresetSelected(presets[bestIndex][0], presets[bestIndex][1]);
            return true;
        }

        // Click was in the viewport region but didn't hit anything — consume
        // anyway to prevent entity picking through the viewcube area
        return true;
    }

    /**
     * Updates the viewport bounds for the top-right corner after a resize.
     */
    public void updateViewportBounds(int screenWidth, int screenHeight) {
        if (viewCubeCam == null) return;

        float left = (float) (screenWidth - VIEWPORT_SIZE_PX - MARGIN_PX) / screenWidth;
        float right = (float) (screenWidth - MARGIN_PX) / screenWidth;
        float bottom = (float) (screenHeight - VIEWPORT_SIZE_PX - MARGIN_PX) / screenHeight;
        float top = (float) (screenHeight - MARGIN_PX) / screenHeight;

        viewCubeCam.setViewPort(left, right, bottom, top);
        viewCubeCam.resize(screenWidth, screenHeight, true);
    }

    /**
     * Removes the viewcube viewport from the render manager.
     */
    public void cleanup(RenderManager renderManager) {
        if (viewPort != null) {
            renderManager.removePostView(viewPort);
        }
    }

    // --- Geometry helpers ---

    private Geometry createArrow(AssetManager assetManager, String name,
                                  Vector3f extent, ColorRGBA color) {
        Arrow arrow = new Arrow(extent);
        arrow.setLineWidth(3f);
        Geometry geo = new Geometry("VCArrow_" + name, arrow);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setLineWidth(3f);
        geo.setMaterial(mat);
        return geo;
    }

    private Geometry createSphere(AssetManager assetManager, String name,
                                   Vector3f position, ColorRGBA color, float radius) {
        Sphere mesh = new Sphere(12, 12, radius);
        Geometry geo = new Geometry("VCSph_" + name, mesh);
        geo.setLocalTranslation(position);
        Material mat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", color);
        geo.setMaterial(mat);
        return geo;
    }

    private Node createLabel(BitmapFont font, String text, Vector3f tipPos, ColorRGBA color) {
        BitmapText txt = new BitmapText(font, false);
        txt.setText(text);
        txt.setSize(LABEL_SIZE);
        txt.setColor(color);
        // Center the text around its origin
        txt.setLocalTranslation(-txt.getLineWidth() / 2f, txt.getLineHeight() / 2f, 0);

        Node labelNode = new Node("VCLabel_" + text);
        labelNode.attachChild(txt);

        // Position beyond the sphere tip
        Vector3f dir = tipPos.normalize();
        labelNode.setLocalTranslation(tipPos.add(dir.mult(LABEL_OFFSET)));

        // Billboard: always face the camera
        labelNode.addControl(new BillboardControl());

        return labelNode;
    }
}

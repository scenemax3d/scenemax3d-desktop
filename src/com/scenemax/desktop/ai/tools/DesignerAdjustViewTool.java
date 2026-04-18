package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class DesignerAdjustViewTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.adjust_view";
    }

    @Override
    public String getDescription() {
        return "Adjusts the active designer viewport. Scene designers support orbit, pan, zoom, camera_mode, frame_selection, and frame_all; UI designers support pan, zoom, and fit.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "Send one viewport action at a time. Scene designer actions mutate the 3D designer camera; UI designer actions mutate the canvas pan/zoom state.")
                .put("properties", new JSONObject()
                        .put("action", new JSONObject()
                                .put("type", "string")
                                .put("description", "Viewport action to perform. Use orbit/pan/zoom/camera_mode/frame_selection/frame_all in scene designers, and pan/zoom/fit in UI designers.")
                                .put("enum", new JSONArray()
                                        .put("orbit")
                                        .put("pan")
                                        .put("zoom")
                                        .put("camera_mode")
                                        .put("frame_selection")
                                        .put("frame_all")
                                        .put("fit")))
                        .put("yaw_degrees", new JSONObject()
                                .put("type", "number")
                                .put("description", "Horizontal orbit delta in degrees for action='orbit'."))
                        .put("pitch_degrees", new JSONObject()
                                .put("type", "number")
                                .put("description", "Vertical orbit delta in degrees for action='orbit'."))
                        .put("right", new JSONObject()
                                .put("type", "number")
                                .put("description", "Scene-camera pan delta along the camera's right axis for action='pan'."))
                        .put("up", new JSONObject()
                                .put("type", "number")
                                .put("description", "Scene-camera pan delta along the camera's up axis for action='pan'."))
                        .put("forward", new JSONObject()
                                .put("type", "number")
                                .put("description", "Scene-camera pan delta along the camera's forward axis for action='pan'."))
                        .put("distance_delta", new JSONObject()
                                .put("type", "number")
                                .put("description", "Scene-camera zoom delta for action='zoom'. Negative values usually move closer."))
                        .put("camera_mode", new JSONObject()
                                .put("type", "string")
                                .put("description", "Scene designer camera interaction mode for action='camera_mode'.")
                                .put("enum", new JSONArray().put("orbit").put("pan")))
                        .put("delta_x", new JSONObject()
                                .put("type", "number")
                                .put("description", "UI canvas horizontal pan delta in pixels for action='pan'."))
                        .put("delta_y", new JSONObject()
                                .put("type", "number")
                                .put("description", "UI canvas vertical pan delta in pixels for action='pan'."))
                        .put("zoom_factor", new JSONObject()
                                .put("type", "number")
                                .put("description", "UI canvas zoom multiplier for action='zoom'. Values above 1 zoom in; below 1 zoom out.")
                                .put("default", 1.0))
                        .put("anchor_x", new JSONObject()
                                .put("type", "number")
                                .put("description", "Optional UI zoom anchor X coordinate in canvas pixels. Defaults to the viewport center."))
                        .put("anchor_y", new JSONObject()
                                .put("type", "number")
                                .put("description", "Optional UI zoom anchor Y coordinate in canvas pixels. Defaults to the viewport center."))
                        .put("padding", new JSONObject()
                                .put("type", "integer")
                                .put("description", "UI fit padding in pixels for action='fit'.")
                                .put("default", 20))
                        .put("padding_scale", new JSONObject()
                                .put("type", "number")
                                .put("description", "Scene framing padding multiplier for action='frame_selection' or 'frame_all'. Larger values leave more margin.")
                                .put("default", 3.0)))
                .put("required", new JSONArray().put("action"));
    }

    @Override
    public JSONObject getOutputSchema() {
        JSONObject sceneViewState = new JSONObject()
                .put("type", "object")
                .put("description", "Updated scene designer camera state after the action.")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Absolute path of the active .smdesign document."))
                        .put("kind", new JSONObject().put("type", "string").put("const", "scene_designer"))
                        .put("cameraMode", new JSONObject().put("type", "string").put("description", "Current scene designer camera interaction mode."))
                        .put("cameraDistance", new JSONObject().put("type", "number").put("description", "Current scene camera distance from its target."))
                        .put("cameraYawDegrees", new JSONObject().put("type", "number").put("description", "Current scene camera yaw in degrees."))
                        .put("cameraPitchDegrees", new JSONObject().put("type", "number").put("description", "Current scene camera pitch in degrees."))
                        .put("cameraTarget", new JSONObject()
                                .put("type", "array")
                                .put("description", "Current scene camera target point as [x, y, z].")
                                .put("items", new JSONObject().put("type", "number"))
                                .put("minItems", 3)
                                .put("maxItems", 3))
                        .put("viewportWidth", new JSONObject().put("type", "integer"))
                        .put("viewportHeight", new JSONObject().put("type", "integer"))
                        .put("selectedEntityId", new JSONObject().put("type", "string").put("description", "Currently selected scene entity id when one is selected."))
                        .put("selectedEntityName", new JSONObject().put("type", "string").put("description", "Currently selected scene entity name when one is selected."))
                        .put("selectedEntityType", new JSONObject().put("type", "string").put("description", "Currently selected scene entity type when one is selected.")))
                .put("required", new JSONArray()
                        .put("path")
                        .put("kind")
                        .put("cameraDistance")
                        .put("cameraYawDegrees")
                        .put("cameraPitchDegrees")
                        .put("cameraTarget")
                        .put("viewportWidth")
                        .put("viewportHeight"));

        JSONObject uiViewState = new JSONObject()
                .put("type", "object")
                .put("description", "Updated UI designer canvas state after the action.")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Absolute path of the active .smui document."))
                        .put("kind", new JSONObject().put("type", "string").put("const", "ui_designer"))
                        .put("zoom", new JSONObject().put("type", "number").put("description", "Current UI canvas zoom level."))
                        .put("panX", new JSONObject().put("type", "number").put("description", "Current UI canvas pan X."))
                        .put("panY", new JSONObject().put("type", "number").put("description", "Current UI canvas pan Y."))
                        .put("activeLayer", new JSONObject().put("type", "string").put("description", "Current active UI layer name when available."))
                        .put("viewportWidth", new JSONObject().put("type", "integer"))
                        .put("viewportHeight", new JSONObject().put("type", "integer"))
                        .put("documentCanvasWidth", new JSONObject().put("type", "integer"))
                        .put("documentCanvasHeight", new JSONObject().put("type", "integer"))
                        .put("selectedWidgetName", new JSONObject().put("type", "string"))
                        .put("selectedWidgetType", new JSONObject().put("type", "string")))
                .put("required", new JSONArray()
                        .put("path")
                        .put("kind")
                        .put("zoom")
                        .put("panX")
                        .put("panY")
                        .put("viewportWidth")
                        .put("viewportHeight"));

        return new JSONObject()
                .put("description", "Returns the updated active designer viewport state after applying the requested action.")
                .put("oneOf", new JSONArray()
                        .put(sceneViewState)
                        .put(uiViewState));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("View adjustment requires a running IDE host.");
        }

        JSONObject data = host.adjustActiveDesignerViewForAutomation(arguments);
        return SceneMaxToolResult.success("Adjusted the active designer view.", data);
    }
}

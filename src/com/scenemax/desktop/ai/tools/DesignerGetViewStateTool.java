package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class DesignerGetViewStateTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.get_view_state";
    }

    @Override
    public String getDescription() {
        return "Returns the current viewport state of the active scene or UI designer.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("description", "No input is required. The tool inspects the currently active scene or UI designer tab.")
                .put("properties", new JSONObject());
    }

    @Override
    public JSONObject getOutputSchema() {
        JSONObject sceneViewState = new JSONObject()
                .put("type", "object")
                .put("description", "Current scene designer camera state.")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Absolute path of the active .smdesign document."))
                        .put("kind", new JSONObject().put("type", "string").put("const", "scene_designer"))
                        .put("cameraMode", new JSONObject().put("type", "string"))
                        .put("cameraDistance", new JSONObject().put("type", "number"))
                        .put("cameraYawDegrees", new JSONObject().put("type", "number"))
                        .put("cameraPitchDegrees", new JSONObject().put("type", "number"))
                        .put("cameraTarget", new JSONObject()
                                .put("type", "array")
                                .put("items", new JSONObject().put("type", "number"))
                                .put("minItems", 3)
                                .put("maxItems", 3))
                        .put("viewportWidth", new JSONObject().put("type", "integer"))
                        .put("viewportHeight", new JSONObject().put("type", "integer"))
                        .put("selectedEntityId", new JSONObject().put("type", "string"))
                        .put("selectedEntityName", new JSONObject().put("type", "string"))
                        .put("selectedEntityType", new JSONObject().put("type", "string")))
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
                .put("description", "Current UI designer canvas state.")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string").put("description", "Absolute path of the active .smui document."))
                        .put("kind", new JSONObject().put("type", "string").put("const", "ui_designer"))
                        .put("zoom", new JSONObject().put("type", "number"))
                        .put("panX", new JSONObject().put("type", "number"))
                        .put("panY", new JSONObject().put("type", "number"))
                        .put("activeLayer", new JSONObject().put("type", "string"))
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
                .put("description", "Returns the current view state for the active scene or UI designer tab.")
                .put("oneOf", new JSONArray().put(sceneViewState).put(uiViewState));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("View inspection requires a running IDE host.");
        }

        JSONObject data = host.getActiveDesignerViewStateForAutomation();
        return SceneMaxToolResult.success("Collected the active designer view state.", data);
    }
}

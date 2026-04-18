package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerValidateResourcesTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.validate_resources";
    }

    @Override
    public String getDescription() {
        return "Reports missing materials, shaders, environment shaders, and model resources in a .smdesign file.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSceneDesigner(path);

        JSONObject root = DesignerAutomationSupport.readJson(path);
        JSONObject resources = DesignerAutomationSupport.validateSceneResources(context, root);
        JSONObject validation = DesignerAutomationSupport.validateDocument(context, path);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("resources", resources);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Validated scene resource references for " + path.getFileName() + ".", data);
    }
}

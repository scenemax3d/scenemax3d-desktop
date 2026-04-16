package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerValidateDocumentTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.validate_document";
    }

    @Override
    public String getDescription() {
        return "Validates a designer document on disk, including scene resource references when applicable.";
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
        JSONObject data = DesignerAutomationSupport.validateDocument(context, path);
        boolean valid = data.optBoolean("valid", false);
        return SceneMaxToolResult.success(valid
                        ? "Validated " + path.getFileName() + "."
                        : "Validation found issues in " + path.getFileName() + ".",
                data);
    }
}

package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerGetDocumentJsonTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.get_document_json";
    }

    @Override
    public String getDescription() {
        return "Returns the JSON document currently stored on disk for a designer document.";
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
        DesignerAutomationSupport.ensureSupportedJsonDesigner(path);

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("kind", DesignerAutomationSupport.kindFor(path));
        data.put("document", DesignerAutomationSupport.readJson(path));
        return SceneMaxToolResult.success("Read designer JSON from " + path.getFileName() + ".", data);
    }
}

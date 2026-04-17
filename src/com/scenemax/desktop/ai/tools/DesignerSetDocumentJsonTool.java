package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerSetDocumentJsonTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.set_document_json";
    }

    @Override
    public String getDescription() {
        return "Replaces a designer JSON document on disk and optionally reloads it in the IDE.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject().put("type", "string"))
                        .put("base", new JSONObject().put("type", "string"))
                        .put("document", new JSONObject().put("type", "object"))
                        .put("json", new JSONObject().put("type", "string"))
                        .put("reload", new JSONObject().put("type", "boolean"))
                        .put("discard_editor_state", new JSONObject().put("type", "boolean"))
                        .put("force", new JSONObject().put("type", "boolean")));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) throws Exception {
        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        DesignerAutomationSupport.ensureDiskFileExists(path);
        DesignerAutomationSupport.ensureSupportedJsonDesigner(path);

        boolean force = optionalBoolean(arguments, "force", false);
        DesignerAutomationSupport.ensureNotDirtyInIde(context, path, force);

        JSONObject document;
        if (arguments.has("document") && arguments.optJSONObject("document") != null) {
            document = new JSONObject(arguments.getJSONObject("document").toString());
        } else if (arguments.has("json")) {
            document = new JSONObject(requireString(arguments, "json"));
        } else {
            throw new IllegalArgumentException("Provide either a document object or a json string.");
        }

        DesignerAutomationSupport.writeJson(path, document);
        JSONObject validation = DesignerAutomationSupport.validateDocument(context, path);

        boolean reloaded = false;
        if (optionalBoolean(arguments, "reload", true)) {
            MainApp host = context.getHost();
            if (host != null) {
                boolean discardEditorState = optionalBoolean(arguments, "discard_editor_state", true);
                reloaded = host.reloadFileFromDiskForAutomation(path.toFile(), discardEditorState);
            }
        }

        JSONObject data = new JSONObject();
        data.put("path", path.toString());
        data.put("kind", DesignerAutomationSupport.kindFor(path));
        data.put("reloaded", reloaded);
        data.put("validation", validation);
        return SceneMaxToolResult.success("Updated designer JSON for " + path.getFileName() + ".", data);
    }
}

package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.MainApp;
import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import org.json.JSONObject;

import java.nio.file.Path;

public class DesignerGenerateCodeTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "designer.generate_code";
    }

    @Override
    public String getDescription() {
        return "Generates the companion .code file for a scene/UI designer document and validates it for syntax errors.";
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
        MainApp host = context.getHost();
        if (host == null) {
            throw new IllegalStateException("Code generation requires a running IDE host.");
        }

        Path path = DesignerAutomationSupport.resolvePathOrActive(context, arguments, "workspace");
        JSONObject data = host.generateDesignerCodeForAutomation(path.toFile());
        if (!data.optBoolean("syntaxValid", false)) {
            return SceneMaxToolResult.error("Generated code has syntax errors.", data);
        }
        return SceneMaxToolResult.success("Generated and validated the designer code.", data);
    }
}

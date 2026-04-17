package com.scenemax.desktop.ai.tools;

import com.scenemax.desktop.ai.SceneMaxToolContext;
import com.scenemax.desktop.ai.SceneMaxToolResult;
import com.scenemaxeng.common.types.RuntimeIssueLog;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Path;

public class RuntimeGetIssueLogTool extends AbstractSceneMaxTool {

    @Override
    public String getName() {
        return "runtime.get_issue_log";
    }

    @Override
    public String getDescription() {
        return "Reads the SceneMax runtime issue log and summarizes syntax/runtime failures for the active project.";
    }

    @Override
    public JSONObject getInputSchema() {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("limit", new JSONObject().put("type", "integer"))
                        .put("issue_type", new JSONObject()
                                .put("type", "string")
                                .put("enum", new org.json.JSONArray().put("all").put("syntax").put("runtime"))));
    }

    @Override
    public SceneMaxToolResult execute(SceneMaxToolContext context, JSONObject arguments) {
        int limit = Math.max(1, optionalInt(arguments, "limit", 20));
        String issueType = optionalString(arguments, "issue_type", "all");
        Path projectRoot = context.getActiveProjectRoot();
        File projectRootFile = projectRoot == null ? null : projectRoot.toFile();

        JSONObject data = RuntimeIssueLog.readIssues(projectRootFile, limit, issueType);
        boolean hasIssues = data.optBoolean("hasIssues", false);
        String summary = hasIssues
                ? "Found syntax or runtime issues in the SceneMax runtime log."
                : "No syntax or runtime issues were found in the SceneMax runtime log.";
        return SceneMaxToolResult.success(summary, data);
    }
}

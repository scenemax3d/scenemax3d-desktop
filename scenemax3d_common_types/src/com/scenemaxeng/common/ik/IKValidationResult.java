package com.scenemaxeng.common.ik;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IKValidationResult {
    private final List<IKValidationIssue> issues = new ArrayList<>();

    public void addError(String field, String message) {
        issues.add(new IKValidationIssue("error", field, message));
    }

    public void addWarning(String field, String message) {
        issues.add(new IKValidationIssue("warning", field, message));
    }

    public boolean isValid() {
        for (IKValidationIssue issue : issues) {
            if ("error".equalsIgnoreCase(issue.getSeverity())) {
                return false;
            }
        }
        return true;
    }

    public List<IKValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public JSONArray toJSONArray() {
        JSONArray array = new JSONArray();
        for (IKValidationIssue issue : issues) {
            array.put(issue.toJSON());
        }
        return array;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("valid", isValid())
                .put("issues", toJSONArray());
    }
}

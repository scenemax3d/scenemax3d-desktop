package com.scenemaxeng.common.weapons;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WeaponValidationResult {
    private final List<WeaponValidationIssue> issues = new ArrayList<>();

    public void addError(String field, String message) {
        issues.add(new WeaponValidationIssue(WeaponValidationIssue.Severity.ERROR, field, message));
    }

    public void addWarning(String field, String message) {
        issues.add(new WeaponValidationIssue(WeaponValidationIssue.Severity.WARNING, field, message));
    }

    public boolean isValid() {
        for (WeaponValidationIssue issue : issues) {
            if (issue.getSeverity() == WeaponValidationIssue.Severity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public List<WeaponValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }

    public JSONArray toJSONArray() {
        JSONArray arr = new JSONArray();
        for (WeaponValidationIssue issue : issues) {
            arr.put(issue.toJSON());
        }
        return arr;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("valid", isValid())
                .put("issues", toJSONArray());
    }
}

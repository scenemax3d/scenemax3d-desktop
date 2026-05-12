package com.scenemaxeng.common.weapons;

import org.json.JSONObject;

public class WeaponValidationIssue {
    public enum Severity {
        ERROR,
        WARNING
    }

    private final Severity severity;
    private final String field;
    private final String message;

    public WeaponValidationIssue(Severity severity, String field, String message) {
        this.severity = severity == null ? Severity.ERROR : severity;
        this.field = field == null ? "" : field;
        this.message = message == null ? "" : message;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("severity", severity.name().toLowerCase())
                .put("field", field)
                .put("message", message);
    }
}

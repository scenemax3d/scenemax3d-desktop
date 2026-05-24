package com.scenemaxeng.common.ik;

import org.json.JSONObject;

public class IKValidationIssue {
    private final String severity;
    private final String field;
    private final String message;

    public IKValidationIssue(String severity, String field, String message) {
        this.severity = severity;
        this.field = field;
        this.message = message;
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put("severity", severity)
                .put("field", field)
                .put("message", message);
    }

    public String getSeverity() {
        return severity;
    }

    public String getField() {
        return field;
    }

    public String getMessage() {
        return message;
    }
}

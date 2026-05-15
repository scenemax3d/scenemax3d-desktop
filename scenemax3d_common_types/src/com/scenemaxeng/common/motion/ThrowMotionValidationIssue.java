package com.scenemaxeng.common.motion;

public class ThrowMotionValidationIssue {
    public enum Severity {
        ERROR,
        WARNING
    }

    private final Severity severity;
    private final String field;
    private final String message;

    public ThrowMotionValidationIssue(Severity severity, String field, String message) {
        this.severity = severity;
        this.field = field;
        this.message = message;
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
}

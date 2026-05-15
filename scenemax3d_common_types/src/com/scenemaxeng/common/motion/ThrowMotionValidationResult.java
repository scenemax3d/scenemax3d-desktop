package com.scenemaxeng.common.motion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ThrowMotionValidationResult {
    private final List<ThrowMotionValidationIssue> issues = new ArrayList<>();

    public void addError(String field, String message) {
        issues.add(new ThrowMotionValidationIssue(ThrowMotionValidationIssue.Severity.ERROR, field, message));
    }

    public void addWarning(String field, String message) {
        issues.add(new ThrowMotionValidationIssue(ThrowMotionValidationIssue.Severity.WARNING, field, message));
    }

    public boolean isValid() {
        for (ThrowMotionValidationIssue issue : issues) {
            if (issue.getSeverity() == ThrowMotionValidationIssue.Severity.ERROR) {
                return false;
            }
        }
        return true;
    }

    public List<ThrowMotionValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}

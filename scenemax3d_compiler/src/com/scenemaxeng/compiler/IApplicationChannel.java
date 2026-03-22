package com.scenemaxeng.compiler;

public interface IApplicationChannel {

    Object getFieldValue(String varName, String fieldName);
    Object getUserDataFieldValue(String varName, String fieldName);
    void handleRuntimeError(String err);

    Object calcDistance(String obj1, String obj2);
    Object calcAngle (String obj1, String obj2);

}

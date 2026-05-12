package com.scenemaxeng.common.weapons;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class WeaponJsonUtil {
    private WeaponJsonUtil() {
    }

    static List<String> stringList(JSONArray arr) {
        List<String> values = new ArrayList<>();
        if (arr == null) {
            return values;
        }
        for (int i = 0; i < arr.length(); i++) {
            String value = arr.optString(i, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    static JSONArray toArray(List<String> values) {
        JSONArray arr = new JSONArray();
        if (values == null) {
            return arr;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                arr.put(value.trim());
            }
        }
        return arr;
    }
}

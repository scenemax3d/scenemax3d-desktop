package com.scenemax.desktop.ai.tools;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UiAuthoringSupportTest {

    private JSONObject buildDoc() {
        JSONObject score = new JSONObject()
                .put("id", "score-id")
                .put("name", "score")
                .put("type", "TEXT_VIEW")
                .put("constraints", new JSONArray().put(new JSONObject()
                        .put("side", "LEFT")
                        .put("targetName", "parent")
                        .put("targetSide", "LEFT")
                        .put("margin", 8)));

        JSONObject hpBar = new JSONObject()
                .put("id", "hpbar-id")
                .put("name", "hpBar")
                .put("type", "IMAGE")
                .put("constraints", new JSONArray().put(new JSONObject()
                        .put("side", "TOP")
                        .put("targetName", "headerPanel")
                        .put("targetSide", "BOTTOM")
                        .put("margin", 4)));

        JSONObject headerPanel = new JSONObject()
                .put("id", "header-id")
                .put("name", "headerPanel")
                .put("type", "PANEL")
                .put("children", new JSONArray().put(score))
                .put("constraints", new JSONArray());

        JSONObject hudLayer = new JSONObject()
                .put("id", "layer-id")
                .put("name", "hud")
                .put("visible", true)
                .put("renderMode", "SCREEN_SPACE")
                .put("widgets", new JSONArray().put(headerPanel).put(hpBar));

        return new JSONObject()
                .put("version", 1)
                .put("name", "ui_doc")
                .put("layers", new JSONArray().put(hudLayer));
    }

    @Test
    public void findWidgetByPathResolvesLayerWidget() {
        JSONObject root = buildDoc();
        UiAuthoringSupport.WidgetHit hit = UiAuthoringSupport.findWidgetByPath(root, "hud.headerPanel");
        assertNotNull(hit);
        assertEquals("headerPanel", hit.widget.getString("name"));
        assertNull(hit.parentWidget);
        assertEquals("hud", hit.layer.getString("name"));
    }

    @Test
    public void findWidgetByPathResolvesNestedChild() {
        JSONObject root = buildDoc();
        UiAuthoringSupport.WidgetHit hit = UiAuthoringSupport.findWidgetByPath(root, "hud.headerPanel.score");
        assertNotNull(hit);
        assertEquals("score", hit.widget.getString("name"));
        assertEquals("headerPanel", hit.parentWidget.getString("name"));
        assertEquals("hud.headerPanel.score", hit.path);
    }

    @Test
    public void findWidgetByPathReturnsNullForUnknown() {
        JSONObject root = buildDoc();
        assertNull(UiAuthoringSupport.findWidgetByPath(root, "hud.nonexistent"));
        assertNull(UiAuthoringSupport.findWidgetByPath(root, "otherLayer.headerPanel"));
    }

    @Test
    public void findWidgetByIdFindsDeeplyNestedWidgets() {
        JSONObject root = buildDoc();
        UiAuthoringSupport.WidgetHit hit = UiAuthoringSupport.findWidgetById(root, "score-id");
        assertNotNull(hit);
        assertEquals("hud.headerPanel.score", hit.path);
    }

    @Test
    public void rewriteConstraintTargetsUpdatesReferences() {
        JSONObject root = buildDoc();
        int count = UiAuthoringSupport.rewriteConstraintTargets(root, "headerPanel", "topBar");
        assertEquals(1, count);
        UiAuthoringSupport.WidgetHit hpBar = UiAuthoringSupport.findWidgetByPath(root, "hud.hpBar");
        assertNotNull(hpBar);
        JSONArray cs = hpBar.widget.getJSONArray("constraints");
        assertEquals("topBar", cs.getJSONObject(0).getString("targetName"));
    }

    @Test
    public void rewriteConstraintTargetsDoesNotTouchParentToken() {
        JSONObject root = buildDoc();
        UiAuthoringSupport.rewriteConstraintTargets(root, "parent", "something");
        UiAuthoringSupport.WidgetHit score = UiAuthoringSupport.findWidgetByPath(root, "hud.headerPanel.score");
        JSONObject constraint = score.widget.getJSONArray("constraints").getJSONObject(0);
        assertEquals("something", constraint.getString("targetName"));
    }

    @Test
    public void collectAllNamesIncludesLayersAndNestedWidgets() {
        JSONObject root = buildDoc();
        var names = UiAuthoringSupport.collectAllNames(root);
        assertTrue(names.contains("hud"));
        assertTrue(names.contains("headerPanel"));
        assertTrue(names.contains("score"));
        assertTrue(names.contains("hpBar"));
    }

    @Test
    public void nextUniqueNameSkipsTakenAndAppendsSuffix() {
        JSONObject root = buildDoc();
        String fresh = UiAuthoringSupport.nextUniqueName(root, "fresh");
        assertEquals("fresh", fresh);
        String taken = UiAuthoringSupport.nextUniqueName(root, "headerPanel");
        assertEquals("headerPanel1", taken);
    }

    @Test
    public void findConstraintReferrersReturnsDependents() {
        JSONObject root = buildDoc();
        List<String> referrers = UiAuthoringSupport.findConstraintReferrers(root, "headerPanel");
        assertEquals(1, referrers.size());
        assertEquals("hud.hpBar", referrers.get(0));
    }
}

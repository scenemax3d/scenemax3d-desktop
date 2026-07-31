package com.scenemaxeng.common.ui.model;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UIWidgetMultiplayerFlagTest {

    @Test
    public void multiplayerFlagRoundTripsForPanelTextAndImageWidgets() {
        assertMultiplayerRoundTrip(UIWidgetType.PANEL);
        assertMultiplayerRoundTrip(UIWidgetType.TEXT_VIEW);
        assertMultiplayerRoundTrip(UIWidgetType.IMAGE);
    }

    @Test
    public void absentMultiplayerFlagDefaultsToFalseForPanelAndImageWidgets() {
        assertMultiplayerDefaultFalse(UIWidgetType.PANEL);
        assertMultiplayerDefaultFalse(UIWidgetType.IMAGE);
    }

    private void assertMultiplayerRoundTrip(UIWidgetType type) {
        UIWidgetDef widget = new UIWidgetDef("shared_" + type.name().toLowerCase(), type);
        widget.setMultiplayer(true);

        UIWidgetDef loaded = UIWidgetDef.fromJSON(new JSONObject(widget.toJSON().toString()));

        assertTrue("Expected multiplayer flag to round-trip for " + type, loaded.isMultiplayer());
    }

    private void assertMultiplayerDefaultFalse(UIWidgetType type) {
        UIWidgetDef widget = new UIWidgetDef("local_" + type.name().toLowerCase(), type);

        UIWidgetDef loaded = UIWidgetDef.fromJSON(new JSONObject(widget.toJSON().toString()));

        assertFalse("Expected multiplayer flag to default false for " + type, loaded.isMultiplayer());
    }
}

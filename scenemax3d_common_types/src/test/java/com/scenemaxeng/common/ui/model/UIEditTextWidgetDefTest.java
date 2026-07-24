package com.scenemaxeng.common.ui.model;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UIEditTextWidgetDefTest {

    @Test
    public void editTextPropertiesRoundTripThroughJson() {
        UIWidgetDef widget = new UIWidgetDef("playerName", UIWidgetType.EDIT_TEXT);
        widget.setText("Adi");
        widget.setTextColor("#EEEEEEFF");
        widget.setFontName("HudFont");
        widget.setFontSize(24);
        widget.setEditTextMultiline(true);
        widget.setEditTextPlaceholder("Name");
        widget.setEditTextBackgroundColor("#101820FF");
        widget.setEditTextFocusedColor("#203040FF");
        widget.setEditTextCursorColor("#FFFFFFFF");
        widget.setEditTextSelectionColor("#4A90E280");

        UIWidgetDef loaded = UIWidgetDef.fromJSON(new JSONObject(widget.toJSON().toString()));

        assertEquals(UIWidgetType.EDIT_TEXT, loaded.getType());
        assertEquals("Adi", loaded.getText());
        assertEquals("#EEEEEEFF", loaded.getTextColor());
        assertEquals("HudFont", loaded.getFontName());
        assertEquals(24f, loaded.getFontSize(), 0.001f);
        assertTrue(loaded.isEditTextMultiline());
        assertEquals("Name", loaded.getEditTextPlaceholder());
        assertEquals("#101820FF", loaded.getEditTextBackgroundColor());
        assertEquals("#203040FF", loaded.getEditTextFocusedColor());
        assertEquals("#FFFFFFFF", loaded.getEditTextCursorColor());
        assertEquals("#4A90E280", loaded.getEditTextSelectionColor());
    }
}

package com.scenemaxeng.common.ui.model;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class UIListViewWidgetDefTest {

    @Test
    public void listViewPropertiesRoundTripThroughJson() {
        UIWidgetDef widget = new UIWidgetDef("players", UIWidgetType.LIST_VIEW);
        widget.setListColumnCount(2);
        widget.setListHeaders(Arrays.asList("Name", "Status"));
        widget.setListColumnWidths(Arrays.asList(180f, 90f));
        widget.setListRows(Arrays.asList(
                Arrays.asList("Alice", "Ready"),
                Arrays.asList("Bob", "Waiting for a longer wrapped message")
        ));
        widget.setListHeaderFontName("HeaderFont");
        widget.setListRowFontName("RowFont");
        widget.setListHeaderFontSize(18);
        widget.setListRowFontSize(13);
        widget.setListViewStyle("dark");
        widget.setListSelectedRowIndex(1);

        UIWidgetDef loaded = UIWidgetDef.fromJSON(new JSONObject(widget.toJSON().toString()));

        assertEquals(UIWidgetType.LIST_VIEW, loaded.getType());
        assertEquals(2, loaded.getListColumnCount());
        assertEquals(Arrays.asList("Name", "Status"), loaded.getListHeaders());
        assertEquals(Arrays.asList(180f, 90f), loaded.getListColumnWidths());
        assertEquals("Bob", loaded.getListRows().get(1).get(0));
        assertEquals("HeaderFont", loaded.getListHeaderFontName());
        assertEquals("RowFont", loaded.getListRowFontName());
        assertEquals(18f, loaded.getListHeaderFontSize(), 0.001f);
        assertEquals(13f, loaded.getListRowFontSize(), 0.001f);
        assertEquals("dark", loaded.getListViewStyle());
        assertEquals(1, loaded.getListSelectedRowIndex());
    }
}

package org.floens.chan.ui.toolbar;

public class ToolbarMenuSubItem {
    private int id;
    private String text;

    public ToolbarMenuSubItem(int id, String text) {
        this.id = id;
        this.text = text;
    }

    public int getId() {
        return id;
    }

    public String getText() {
        return text;
    }
}

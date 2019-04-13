package org.floens.chan.core.model.export;

import com.google.gson.annotations.SerializedName;

import org.floens.chan.core.manager.FilterType;

public class ExportedFilter {
    @SerializedName("enabled")
    private boolean enabled = true;
    @SerializedName("type")
    private int type = FilterType.SUBJECT.flag | FilterType.COMMENT.flag;
    @SerializedName("pattern")
    private String pattern;
    @SerializedName("all_boards")
    private boolean allBoards = true;
    @SerializedName("boards")
    private String boards = "";
    @SerializedName("action")
    private int action;
    @SerializedName("color")
    private int color;

    public ExportedFilter(
            boolean enabled,
            int type,
            String pattern,
            boolean allBoards,
            String boards,
            int action,
            int color
    ) {
        this.enabled = enabled;
        this.type = type;
        this.pattern = pattern;
        this.allBoards = allBoards;
        this.boards = boards;
        this.action = action;
        this.color = color;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getType() {
        return type;
    }

    public String getPattern() {
        return pattern;
    }

    public boolean isAllBoards() {
        return allBoards;
    }

    public String getBoards() {
        return boards;
    }

    public int getAction() {
        return action;
    }

    public int getColor() {
        return color;
    }
}

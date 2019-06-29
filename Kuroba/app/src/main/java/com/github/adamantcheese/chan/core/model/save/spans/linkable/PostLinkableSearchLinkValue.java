package com.github.adamantcheese.chan.core.model.save.spans.linkable;

import com.github.adamantcheese.chan.core.model.save.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

public class PostLinkableSearchLinkValue extends PostLinkableValue {
    @SerializedName("board")
    private String board;
    @SerializedName("search")
    private String search;

    public PostLinkableSearchLinkValue(
            SerializablePostLinkableSpan.PostLinkableType type,
            String board,
            String search) {
        super(type);

        this.board = board;
        this.search = search;
    }

    public String getBoard() {
        return board;
    }

    public String getSearch() {
        return search;
    }
}

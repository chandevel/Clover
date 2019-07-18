package com.github.adamantcheese.chan.core.model.save.spans.linkable;

import com.github.adamantcheese.chan.core.model.save.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

public class PostLinkableBoardLinkValue extends PostLinkableValue {
    @SerializedName("board_link")
    private String boardLink;

    public PostLinkableBoardLinkValue(
            SerializablePostLinkableSpan.PostLinkableType type,
            String boardLink) {
        super(type);

        this.boardLink = boardLink;
    }

    public String getBoardLink() {
        return boardLink;
    }
}

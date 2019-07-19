package com.github.adamantcheese.chan.core.model.save.spans.linkable;

import com.github.adamantcheese.chan.core.model.save.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

public class PostLinkableLinkValue extends PostLinkableValue {
    @SerializedName("link")
    private String link;

    public PostLinkableLinkValue(SerializablePostLinkableSpan.PostLinkableType type, String link) {
        super(type);

        this.link = link;
    }

    public String getLink() {
        return link;
    }
}

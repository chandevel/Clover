package com.github.adamantcheese.chan.core.model.save.spans.linkable;

import com.github.adamantcheese.chan.core.model.save.spans.SerializablePostLinkableSpan;
import com.google.gson.annotations.SerializedName;

public abstract class PostLinkableValue {
    @SerializedName("post_linkable_value_type")
    protected int type;

    public PostLinkableValue(SerializablePostLinkableSpan.PostLinkableType type) {
        this.type = type.getTypeValue();
    }

    public int getType() {
        return type;
    }
}


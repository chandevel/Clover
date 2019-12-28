package com.github.adamantcheese.chan.core.model.save.spans;

import com.google.gson.annotations.SerializedName;

public class SerializableStyleSpan {
    @SerializedName("style")
    private int style;

    public SerializableStyleSpan(int style) {
        this.style = style;
    }

    public int getStyle() {
        return style;
    }
}

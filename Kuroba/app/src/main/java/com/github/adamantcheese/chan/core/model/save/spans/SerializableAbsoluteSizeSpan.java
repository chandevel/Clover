package com.github.adamantcheese.chan.core.model.save.spans;

import com.google.gson.annotations.SerializedName;

public class SerializableAbsoluteSizeSpan {
    @SerializedName("size")
    private int size;

    public SerializableAbsoluteSizeSpan(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}

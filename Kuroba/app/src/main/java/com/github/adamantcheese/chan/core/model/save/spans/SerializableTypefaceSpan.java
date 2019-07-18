package com.github.adamantcheese.chan.core.model.save.spans;

import com.google.gson.annotations.SerializedName;

public class SerializableTypefaceSpan {
    @SerializedName("family")
    private String family;

    public SerializableTypefaceSpan(String family) {
        this.family = family;
    }

    public String getFamily() {
        return family;
    }
}

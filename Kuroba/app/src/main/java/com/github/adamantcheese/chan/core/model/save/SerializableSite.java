package com.github.adamantcheese.chan.core.model.save;

import com.google.gson.annotations.SerializedName;

public class SerializableSite {
    @SerializedName("site_id")
    private int siteId;

    public SerializableSite(int siteId) {
        this.siteId = siteId;
    }
}

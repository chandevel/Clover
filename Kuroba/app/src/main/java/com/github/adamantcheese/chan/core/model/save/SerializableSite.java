package com.github.adamantcheese.chan.core.model.save;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class SerializableSite {
    @SerializedName("site_id")
    private int siteId;

    public SerializableSite(int siteId) {
        this.siteId = siteId;
    }

    @Override
    public int hashCode() {
        return 31 * siteId;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (this.getClass() != other.getClass()) {
            return false;
        }

        SerializableSite otherSite = (SerializableSite) other;
        return this.siteId == otherSite.siteId;
    }
}

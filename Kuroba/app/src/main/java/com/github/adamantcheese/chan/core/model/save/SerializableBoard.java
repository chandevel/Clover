package com.github.adamantcheese.chan.core.model.save;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class SerializableBoard {
    @SerializedName("id")
    public int id;
    @SerializedName("site_id")
    public int siteId;
    @SerializedName("serializable_site")
    public SerializableSite site;
    @SerializedName("saved")
    public boolean saved;
    @SerializedName("order")
    public int order;
    @SerializedName("name")
    public String name;
    @SerializedName("code")
    public String code;

    public SerializableBoard(
            int id, int siteId, SerializableSite site, boolean saved, int order, String name, String code
    ) {
        this.id = id;
        this.siteId = siteId;
        this.site = site;
        this.saved = saved;
        this.order = order;
        this.name = name;
        this.code = code;
    }

    @Override
    public int hashCode() {
        return Objects.hash(siteId, name, code);
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

        SerializableBoard otherBoard = (SerializableBoard) other;
        return this.siteId == otherBoard.siteId && this.name.equals(otherBoard.name)
                && this.code.equals(otherBoard.code);
    }
}

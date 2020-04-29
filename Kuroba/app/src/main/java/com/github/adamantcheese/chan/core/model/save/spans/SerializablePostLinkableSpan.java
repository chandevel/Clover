package com.github.adamantcheese.chan.core.model.save.spans;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class SerializablePostLinkableSpan {
    @SerializedName("key")
    private String key;
    @SerializedName("type")
    private int postLinkableType;
    @SerializedName("post_linkable_value_json")
    @Expose(deserialize = false)
    private String postLinkableValueJson;

    public SerializablePostLinkableSpan(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public PostLinkableType getPostLinkableType() {
        return PostLinkableType.from(postLinkableType);
    }

    public void setPostLinkableType(int postLinkableType) {
        this.postLinkableType = postLinkableType;
    }

    public String getPostLinkableValueJson() {
        return postLinkableValueJson;
    }

    public void setPostLinkableValueJson(String postLinkableValueJson) {
        this.postLinkableValueJson = postLinkableValueJson;
    }

    public enum PostLinkableType {
        Quote(0),
        Link(1),
        Spoiler(2),
        Thread(3),
        Board(4),
        Search(5);

        private int typeValue;

        PostLinkableType(int value) {
            this.typeValue = value;
        }

        public int getTypeValue() {
            return typeValue;
        }

        public static PostLinkableType from(int value) {
            switch (value) {
                case 0:
                    return Quote;
                case 1:
                    return Link;
                case 2:
                    return Spoiler;
                case 3:
                    return Thread;
                case 4:
                    return Board;
                case 5:
                    return Search;
                default:
                    throw new IllegalArgumentException("Not implemented for value = " + value);
            }
        }
    }
}

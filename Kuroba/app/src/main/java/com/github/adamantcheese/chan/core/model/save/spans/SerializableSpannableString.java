package com.github.adamantcheese.chan.core.model.save.spans;

import androidx.annotation.Nullable;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class SerializableSpannableString {
    @SerializedName("span_info_list")
    private List<SpanInfo> spanInfoList;
    @SerializedName("text")
    private String text;

    public SerializableSpannableString() {
        this.spanInfoList = new ArrayList<>();
    }

    public void addSpanInfo(SpanInfo spanInfo) {
        spanInfoList.add(spanInfo);
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<SpanInfo> getSpanInfoList() {
        return spanInfoList;
    }

    public String getText() {
        return text;
    }

    public static class SpanInfo {
        @SerializedName("span_start")
        private int spanStart;
        @SerializedName("span_end")
        private int spanEnd;
        @SerializedName("flags")
        private int flags;
        @SerializedName("span_type")
        private int spanType;
        @SerializedName("span_data")
        @Expose(deserialize = false)
        @Nullable
        private String spanData;

        public SpanInfo(SpanType spanType, int spanStart, int spanEnd, int flags) {
            this.spanType = spanType.getSpanTypeValue();
            this.spanStart = spanStart;
            this.spanEnd = spanEnd;
            this.flags = flags;
        }

        public int getSpanStart() {
            return spanStart;
        }

        public int getSpanEnd() {
            return spanEnd;
        }

        public int getFlags() {
            return flags;
        }

        public int getSpanType() {
            return spanType;
        }

        @Nullable
        public String getSpanData() {
            return spanData;
        }

        public void setSpanData(@Nullable String spanData) {
            this.spanData = spanData;
        }
    }

    public enum SpanType {
        ForegroundColorSpanHashedType(0),
        BackgroundColorSpanHashedType(1),
        StrikethroughSpanType(2),
        StyleSpanType(3),
        TypefaceSpanType(4),
        AbsoluteSizeSpanHashed(5),
        PostLinkable(6);

        private int spanTypeValue;

        SpanType(int value) {
            this.spanTypeValue = value;
        }

        public int getSpanTypeValue() {
            return spanTypeValue;
        }

        public static SpanType from(int value) {
            switch (value) {
                case 0:
                    return ForegroundColorSpanHashedType;
                case 1:
                    return BackgroundColorSpanHashedType;
                case 2:
                    return StrikethroughSpanType;
                case 3:
                    return StyleSpanType;
                case 4:
                    return TypefaceSpanType;
                case 5:
                    return AbsoluteSizeSpanHashed;
                case 6:
                    return PostLinkable;
                default:
                    throw new IllegalArgumentException("Not implemented for value = " + value);
            }
        }
    }
}

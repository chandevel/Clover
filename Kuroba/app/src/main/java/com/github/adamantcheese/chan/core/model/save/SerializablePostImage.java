package com.github.adamantcheese.chan.core.model.save;

import com.google.gson.annotations.SerializedName;

public class SerializablePostImage {
    @SerializedName("original_name")
    private String originalName;
    @SerializedName("filename")
    private String filename;
    @SerializedName("extension")
    private String extension;
    @SerializedName("image_width")
    private int imageWidth;
    @SerializedName("image_height")
    private int imageHeight;
    @SerializedName("spoiler")
    private boolean spoiler;
    @SerializedName("size")
    private long size;
    @SerializedName("type")
    private String type;

    public SerializablePostImage(
            String originalName,
            String filename,
            String extension,
            int imageWidth,
            int imageHeight,
            boolean spoiler,
            long size,
            String type) {
        this.originalName = originalName;
        this.filename = filename;
        this.extension = extension;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.spoiler = spoiler;
        this.size = size;
        this.type = type;
    }
}

package com.github.adamantcheese.chan.core.model.save;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class SerializablePostImage {
    @SerializedName("original_name")
    private String originalName;
    @SerializedName("filename")
    private String filename;
    @SerializedName("extension")
    private String extension;
    @SerializedName("image_url")
    private String imageUrl;
    @SerializedName("thumbnail_url")
    private String thumbnailUrlString;
    @SerializedName("spoiler_url")
    private String spoilerUrlString;
    @SerializedName("image_width")
    private int imageWidth;
    @SerializedName("image_height")
    private int imageHeight;
    @SerializedName("spoiler")
    private boolean spoiler;
    @SerializedName("size")
    private long size;
    @SerializedName("file_hash")
    private String fileHash;

    public SerializablePostImage(
            String originalName,
            String filename,
            String extension,
            String imageUrl,
            String thumbnailUrlString,
            String spoilerUrlString,
            int imageWidth,
            int imageHeight,
            boolean spoiler,
            long size,
            String fileHash
    ) {
        this.originalName = originalName;
        this.filename = filename;
        this.extension = extension;
        this.imageUrl = imageUrl;
        this.thumbnailUrlString = thumbnailUrlString;
        this.spoilerUrlString = spoilerUrlString;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.spoiler = spoiler;
        this.size = size;
        this.fileHash = fileHash;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getFilename() {
        return filename;
    }

    public String getExtension() {
        return extension;
    }

    @Nullable
    public String getImageUrl() {
        return imageUrl;
    }

    public String getThumbnailUrlString() {
        return thumbnailUrlString;
    }

    public String getSpoilerUrlString() {
        return spoilerUrlString;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public boolean isSpoiler() {
        return spoiler;
    }

    public long getSize() {
        return size;
    }

    @Nullable
    public String getFileHash() {
        return fileHash;
    }

    @Override
    public int hashCode() {
        return 31 * originalName.hashCode() + 31 * filename.hashCode();
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

        SerializablePostImage otherPostImage = (SerializablePostImage) other;

        return this.originalName.equals(otherPostImage.originalName) && this.filename.equals(otherPostImage.filename);
    }
}

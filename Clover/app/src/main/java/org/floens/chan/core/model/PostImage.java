package org.floens.chan.core.model;

public class PostImage {
    public enum Type {
        STATIC, GIF, MOVIE
    }

    public String originalName;
    public String thumbnailUrl;
    public String imageUrl;
    public String filename;
    public String extension;
    public int imageWidth;
    public int imageHeight;
    public boolean spoiler;

    public Type type;

    public PostImage(String originalName, String thumbnailUrl, String imageUrl, String filename, String extension, int imageWidth, int imageHeight, boolean spoiler) {
        this.originalName = originalName;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrl = imageUrl;
        this.filename = filename;
        this.extension = extension;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.spoiler = spoiler;

        switch (extension) {
            case "gif":
                type = Type.GIF;
                break;
            case "webm":
                type = Type.MOVIE;
                break;
            default:
                type = Type.STATIC;
                break;
        }
    }
}

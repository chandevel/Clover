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

    public Type type;

    public PostImage(String originalName, String thumbnailUrl, String imageUrl, String filename, String extension, int imageWidth, int imageHeight) {
        this.originalName = originalName;
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrl = imageUrl;
        this.filename = filename + "." + extension;
        this.extension = extension;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;

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

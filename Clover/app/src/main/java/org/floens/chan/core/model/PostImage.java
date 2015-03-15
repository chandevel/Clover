package org.floens.chan.core.model;

public class PostImage {
    public String thumbnailUrl;
    public String imageUrl;
    public String filename;
    public int imageWidth;
    public int imageHeight;

    public PostImage(String thumbnailUrl, String imageUrl, String filename, int imageWidth, int imageHeight) {
        this.thumbnailUrl = thumbnailUrl;
        this.imageUrl = imageUrl;
        this.filename = filename;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }
}

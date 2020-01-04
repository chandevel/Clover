package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.save.SerializablePostImage;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;

public class PostImageMapper {

    public static SerializablePostImage toSerializablePostImage(PostImage postImage) {
        return new SerializablePostImage(
                postImage.serverFilename,
                postImage.filename,
                postImage.extension,
                postImage.imageUrl.toString(),
                postImage.thumbnailUrl.toString(),
                postImage.spoilerThumbnailUrl.toString(),
                postImage.imageWidth,
                postImage.imageHeight,
                postImage.spoiler,
                postImage.size,
                postImage.fileHash
        );
    }

    public static List<SerializablePostImage> toSerializablePostImageList(List<PostImage> postImageList) {
        List<SerializablePostImage> serializablePostImageList = new ArrayList<>(postImageList.size());

        for (PostImage postImage : postImageList) {
            serializablePostImageList.add(toSerializablePostImage(postImage));
        }

        return serializablePostImageList;
    }

    public static PostImage fromSerializablePostImage(SerializablePostImage serializablePostImage) {
        HttpUrl imageUrl = null;

        if (serializablePostImage.getImageUrl() != null) {
            imageUrl = HttpUrl.parse(serializablePostImage.getImageUrl());
        }

        return new PostImage.Builder().serverFilename(serializablePostImage.getOriginalName())
                .filename(serializablePostImage.getFilename())
                .extension(serializablePostImage.getExtension())
                .imageUrl(imageUrl)
                .thumbnailUrl(HttpUrl.parse(serializablePostImage.getThumbnailUrlString()))
                .spoilerThumbnailUrl(HttpUrl.parse(serializablePostImage.getSpoilerUrlString()))
                .imageWidth(serializablePostImage.getImageWidth())
                .imageHeight(serializablePostImage.getImageHeight())
                .spoiler(serializablePostImage.isSpoiler())
                .size(serializablePostImage.getSize())
                .fileHash(serializablePostImage.getFileHash())
                .build();
    }

    public static List<PostImage> fromSerializablePostImageList(List<SerializablePostImage> serializablePostImageList) {
        List<PostImage> postImageList = new ArrayList<>(serializablePostImageList.size());

        for (SerializablePostImage serializablePostImage : serializablePostImageList) {
            postImageList.add(fromSerializablePostImage(serializablePostImage));
        }

        return postImageList;
    }
}

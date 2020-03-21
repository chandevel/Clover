package com.github.adamantcheese.chan.core.mapper;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.save.SerializablePostImage;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;

public class PostImageMapper {

    @Nullable
    public static SerializablePostImage toSerializablePostImage(PostImage postImage) {
        if (postImage.imageUrl == null) {
            return null;
        }

        return new SerializablePostImage(
                postImage.serverFilename,
                postImage.filename,
                postImage.extension,
                postImage.imageUrl.toString(),
                postImage.thumbnailUrl.toString(),
                postImage.spoilerThumbnailUrl.toString(),
                postImage.imageWidth,
                postImage.imageHeight,
                postImage.spoiler(),
                postImage.size,
                postImage.fileHash
        );
    }

    public static List<SerializablePostImage> toSerializablePostImageList(List<PostImage> postImageList) {
        List<SerializablePostImage> serializablePostImageList = new ArrayList<>(postImageList.size());

        for (PostImage postImage : postImageList) {
            SerializablePostImage serializablePostImage = toSerializablePostImage(postImage);
            if (serializablePostImage == null) {
                continue;
            }

            serializablePostImageList.add(serializablePostImage);
        }

        return serializablePostImageList;
    }

    @Nullable
    public static PostImage fromSerializablePostImage(SerializablePostImage serializablePostImage) {
        HttpUrl imageUrl = null;

        if (serializablePostImage.getImageUrl() != null) {
            imageUrl = HttpUrl.parse(serializablePostImage.getImageUrl());
        }

        if (imageUrl == null) {
            return null;
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
                //hack for 2ch, everything else encodes their image hashes
                .fileHash(serializablePostImage.getFileHash(), !serializablePostImage.getImageUrl().contains("2ch.hk"))
                .build();
    }

    public static List<PostImage> fromSerializablePostImageList(List<SerializablePostImage> serializablePostImageList) {
        List<PostImage> postImageList = new ArrayList<>(serializablePostImageList.size());

        for (SerializablePostImage serializablePostImage : serializablePostImageList) {
            PostImage postImage = fromSerializablePostImage(serializablePostImage);
            if (postImage == null) {
                continue;
            }

            postImageList.add(postImage);
        }

        return postImageList;
    }
}

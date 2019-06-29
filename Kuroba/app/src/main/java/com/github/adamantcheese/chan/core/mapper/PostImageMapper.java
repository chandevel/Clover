package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.save.SerializablePostImage;

import java.util.ArrayList;
import java.util.List;

public class PostImageMapper {

    public static SerializablePostImage toSerializablePostImage(PostImage postImage) {
        return new SerializablePostImage(
                postImage.originalName,
                postImage.filename,
                postImage.extension,
                postImage.imageWidth,
                postImage.imageHeight,
                postImage.spoiler,
                postImage.size
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
        return new PostImage.Builder()
                .originalName(serializablePostImage.getOriginalName())
                .filename(serializablePostImage.getFilename())
                .extension(serializablePostImage.getExtension())
                .imageWidth(serializablePostImage.getImageWidth())
                .imageHeight(serializablePostImage.getImageHeight())
                .spoiler(serializablePostImage.isSpoiler())
                .size(serializablePostImage.getSize())
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

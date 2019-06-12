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
                postImage.size,
                postImage.type.name()
        );
    }

    public static List<SerializablePostImage> toSerializablePostImageList(List<PostImage> postImageList) {
        List<SerializablePostImage> serializablePostImageList = new ArrayList<>(postImageList.size());

        for (PostImage postImage : postImageList) {
            serializablePostImageList.add(toSerializablePostImage(postImage));
        }

        return serializablePostImageList;
    }

}

package com.github.adamantcheese.chan.features.embedding.embedders.impl;

import android.graphics.Bitmap;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.JsonEmbedder;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.HttpUrl;

public class ImgurEmbedder
        extends JsonEmbedder {
    private static final Pattern IMGUR_PATTERN = Pattern.compile("https?://imgur.(?:com|io)/(a/)?(\\w{7})/?");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "imgur");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.imgurIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return IMGUR_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        if ("a/".equals(matcher.group(1))) {
            return HttpUrl.get("https://api.imgur.com/3/album/" + matcher.group(2));
        } else {
            return HttpUrl.get("https://api.imgur.com/3/image/" + matcher.group(2));
        }
    }

    @Override
    public Headers getExtraHeaders() {
        return new Headers.Builder().add("Authorization", "Client-ID 1d8d9b36339e0e2").build();
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, JsonReader> getInternalConverter() {
        return input -> {
            List<PostImage> generatedImages = new ArrayList<>();
            String title = null;
            input.beginObject();
            input.nextName(); // "data"
            input.beginObject();
            while (input.hasNext()) {
                switch (input.nextName()) {
                    case "link": // single image
                        HttpUrl sourceUrl = HttpUrl.get(input.nextString());
                        PostImage generatedImage = generateThumbnailImage(sourceUrl);
                        generatedImages.add(generatedImage);
                        break;
                    case "title": // album images contain this
                        if (input.peek() == JsonToken.NULL) {
                            input.nextNull();
                        } else {
                            title = input.nextString();
                        }
                        break;
                    case "images": // album images list
                        input.beginArray();
                        while (input.hasNext()) {
                            input.beginObject();
                            while (input.hasNext()) {
                                if ("link".equals(input.nextName())) {
                                    HttpUrl srcUrl = HttpUrl.get(input.nextString());
                                    PostImage generated = generateThumbnailImage(srcUrl);
                                    generatedImages.add(generated);
                                } else {
                                    input.skipValue();
                                }
                            }
                            input.endObject();
                        }
                        input.endArray();
                        break;
                    default:
                        input.skipValue();
                        break;
                }
            }
            input.endObject();
            while (input.hasNext()) {
                input.skipValue();
            }
            input.endObject();
            return new EmbedResult(title != null ? title : "Imgur Album", null, generatedImages);
        };
    }

    // Also used for i.imgur.com links in the EMBED_IMAGES style action
    public static PostImage generateThumbnailImage(HttpUrl sourceUrl) {
        PostImage.Builder builder = new PostImage.Builder().isInlined();
        Pair<String, String> split = StringUtils.splitExtension(sourceUrl);
        HttpUrl thumbUrl = HttpUrl.get(split.first + "t." + split.second);
        String filename = split.first.substring(split.first.lastIndexOf("/") + 1);
        builder
                .imageUrl(sourceUrl)
                .thumbnailUrl(thumbUrl)
                .filename(filename)
                .serverFilename(filename)
                .isInlined()
                .extension(split.second);
        return builder.build();
    }
}

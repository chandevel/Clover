package com.github.adamantcheese.chan.features.embedding.embedders.impl;

import android.graphics.Bitmap;
import android.util.JsonReader;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.JsonEmbedder;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Headers;
import okhttp3.HttpUrl;

/**
 * Note that this embedder doesn't take care of i.imgur.com links, since those are direct images;
 * this one takes care of standard links that might be albums or regular page links instead of raw links
 */
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
            return HttpUrl.get("https://api.imgur.com/3/album/" + matcher.group(2) + "/images");
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
            List<PostImage> generated = new ArrayList<>();
            input.beginObject();
            input.nextName(); // "data"
            switch (input.peek()) {
                case BEGIN_OBJECT: // single image
                    input.beginObject();
                    generated.add(imageFromJson(input));
                    input.endObject();
                    break;
                case BEGIN_ARRAY: // album
                    input.beginArray();
                    while (input.hasNext()) {
                        input.beginObject();
                        generated.add(imageFromJson(input));
                        input.endObject();
                    }
                    input.endArray();
                    break;
            }
            return new EmbedResult("Imgur album", null, generated);
        };
    }

    private PostImage imageFromJson(JsonReader reader)
            throws IOException {
        PostImage.Builder builder = new PostImage.Builder().isInlined();
        while (reader.hasNext()) {
            if ("link".equals(reader.nextName())) {
                HttpUrl sourceUrl = HttpUrl.get(reader.nextString());
                Pair<String, String> split = StringUtils.splitExtension(sourceUrl);
                HttpUrl thumbUrl = HttpUrl.get(split.first + "t." + split.second);
                String filename = split.first.substring(split.first.lastIndexOf("/") + 1);
                builder
                        .imageUrl(sourceUrl)
                        .thumbnailUrl(thumbUrl)
                        .filename(filename)
                        .serverFilename(filename)
                        .extension(split.second);
            } else {
                reader.skipValue();
            }
        }
        return builder.build();
    }
}

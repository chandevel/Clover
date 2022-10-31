package com.github.adamantcheese.chan.features.embedding.embedders.impl;

import android.graphics.Bitmap;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.HtmlEmbedder;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

/**
 * Note that this embedder doesn't take care of i.imgur.com links, since those are direct images;
 * this one takes care of standard links that might be albums or regular page links instead of raw links
 */
public class ImgurEmbedder
        extends HtmlEmbedder {
    private static final Pattern IMGUR_PATTERN = Pattern.compile("https?://imgur.com/(a/)?(\\w{7})/?");

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
            return HttpUrl.get("https://imgur.com/a/" + matcher.group(2));
        } else {
            return HttpUrl.get("https://imgur.com/" + matcher.group(1));
        }
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, Document> getInternalConverter() {
        // for videos
        // .Gallery-Content--media > .PostVideo > .PostVideo-video-wrapper video source
        // for images
        // .Gallery-Content--media > .imageContainer img
        // for both, get the src attribute of that element for the actual content
        // TODO while this should work in theory, imgur's site returns a noscript page to okhttp so this finds no images
        return input -> {
            Elements allImages = new Elements();
            allImages.addAll(input.select(".Gallery-Content--media > .imageContainer > img"));
            allImages.addAll(input.select(".Gallery-Content--media > .PostVideo > .PostVideo-video-wrapper video source"));
            List<PostImage> generated = new ArrayList<>();
            for (Element element : allImages) {
                HttpUrl sourceUrl = HttpUrl.get(element.attr("src"));
                Pair<String, String> split = StringUtils.splitExtension(sourceUrl);
                HttpUrl thumbUrl = HttpUrl.get(split.first + "t." + split.second);
                String filename = split.first.substring(split.first.lastIndexOf("/") + 1);
                generated.add(new PostImage.Builder()
                        .serverFilename(filename)
                        .thumbnailUrl(thumbUrl)
                        .imageUrl(sourceUrl)
                        .filename(filename)
                        .extension(split.second)
                        .isInlined()
                        .build());
            }
            return new EmbedResult(input.title(), null, generated);
        };
    }
}

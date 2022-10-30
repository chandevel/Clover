package com.github.adamantcheese.chan.features.embedding.embedders.impl;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedNoTitleException;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.features.embedding.embedders.base.JsonEmbedder;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class StrawpollEmbedder
        extends JsonEmbedder {
    private static final Pattern STRAWPOLL_LINK_PATTERN =
            Pattern.compile("https?://strawpoll.com/polls/(\\w{11})(?:/|\\b)");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "strawpoll");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.strawpollIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return STRAWPOLL_LINK_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://api.strawpoll.com/v3/polls/" + matcher.group(1));
    }

    @Override
    public NetUtilsClasses.Converter<EmbedResult, JsonReader> getInternalConverter() {
        return input -> {
            String title = null;

            input.beginObject();
            while (input.hasNext()) {
                String name = input.nextName();
                switch (name) {
                    case "title":
                        title = input.nextString().trim();
                        break;
                    case "type":
                        String type = input.nextString();
                        if(!"multiple_choice".equals(type)) {
                            title += " [" + StringUtils.caseAndSpace(type, "_", true) + "]";
                        }
                        break;
                    default:
                        input.skipValue();
                        break;
                }
            }
            input.endObject();

            if (title == null) throw new EmbedNoTitleException();

            return new EmbedResult(title, null, null);
        };
    }
}

package com.github.adamantcheese.chan.core.site.sites.dvach;

import android.graphics.Color;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanPostParser;
import com.github.adamantcheese.chan.core.site.parser.style.comment.ChanCommentAction;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DvachPostParser
        extends VichanPostParser {

    private final Pattern colorPattern = Pattern.compile("color:rgb\\((\\d+),(\\d+),(\\d+)\\);");

    public DvachPostParser(ChanCommentAction chanCommentAction) {
        super(chanCommentAction);
    }

    @Override
    public Post parse(@NonNull Theme theme, Post.Builder builder, List<Filter> filters, Callback callback) {
        builder.name = Parser.unescapeEntities(builder.name, false);
        parseNameForColor(builder);
        return super.parse(theme, builder, filters, callback);
    }

    private void parseNameForColor(Post.Builder builder) {
        CharSequence nameRaw = builder.name;
        try {
            String name = nameRaw.toString();

            Document document = Jsoup.parseBodyFragment(name);
            Element span = document.body().getElementsByTag("span").first();
            if (span != null) {
                String style = span.attr("style");
                builder.posterId = span.text();
                builder.name = document.body().textNodes().get(0).text().trim();

                if (!TextUtils.isEmpty(style)) {
                    style = style.replace(" ", "");

                    Matcher matcher = colorPattern.matcher(style);
                    if (matcher.find()) {
                        int r = Integer.parseInt(matcher.group(1));
                        int g = Integer.parseInt(matcher.group(2));
                        int b = Integer.parseInt(matcher.group(3));

                        builder.idColor = Color.rgb(r, g, b);
                    }
                }
            }
        } catch (Exception e) {
            Logger.e(this, "Error parsing name html", e);
        }
    }
}
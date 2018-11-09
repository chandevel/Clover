package org.floens.chan.core.site.sites.dvach;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.TextUtils;

import org.floens.chan.core.model.Post;
import org.floens.chan.core.site.common.DefaultPostParser;
import org.floens.chan.core.site.parser.CommentParser;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.utils.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DvachPostParser extends DefaultPostParser {

    private Pattern colorPattern = Pattern.compile("color:rgb\\((\\d+),(\\d+),(\\d+)\\);");
    private static final String TAG = "DvachPostParser";

    private CommentParser commentParser;

    public DvachPostParser(CommentParser commentParser) {
        super(commentParser);
    }

    @Override
    public Post parse(Theme theme, Post.Builder builder, Callback callback) {
        builder.name = Parser.unescapeEntities(builder.name, false);
        parseNameForColor(builder);
        return super.parse(theme, builder, callback);
    }

    private void parseNameForColor(Post.Builder builder) {
        CharSequence total = new SpannableString("");
        CharSequence nameRaw = builder.name;
        try {
            String name = nameRaw.toString();

            Document document = Jsoup.parseBodyFragment(name);
            Element span = document.body().getElementsByTag("span").first();
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

                    int hexColor = Color.rgb(r, g, b);
                    builder.idColor = hexColor;
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "Error parsing name html", e);
        }
    }
}

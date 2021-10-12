package com.github.adamantcheese.chan.core.site.sites.dvach;

import android.text.SpannedString;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanPostParser;
import com.github.adamantcheese.chan.core.site.parser.style.HtmlDocumentAction;
import com.github.adamantcheese.chan.core.site.parser.style.HtmlElementAction;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.List;

public class DvachPostParser
        extends VichanPostParser {

    public DvachPostParser() {
        super();
    }

    @Override
    public Post parse(
            Post.Builder builder, @NonNull Theme theme, HtmlElementAction elementAction, List<Filter> filters,
            Callback callback
    ) {
        Document nameDoc = Jsoup.parseBodyFragment(builder.name);
        SpannedString nameStyled =
                new HtmlDocumentAction(new HtmlElementAction()).style(nameDoc, null, theme, builder, callback);
        ForegroundColorSpanHashed[] idColorSpan =
                nameStyled.getSpans(0, nameStyled.length(), ForegroundColorSpanHashed.class);
        if (idColorSpan.length > 0) {
            builder.idColor = idColorSpan[0].getForegroundColor();
        }
        String[] nameIdSplit = nameStyled.toString().replace('\u00A0', '\u0020').split("ID:");
        builder.name = nameIdSplit[0].trim();
        if (nameIdSplit.length > 1) {
            builder.posterId = nameIdSplit[1].trim();
        }
        return super.parse(builder, theme, elementAction, filters, callback);
    }
}
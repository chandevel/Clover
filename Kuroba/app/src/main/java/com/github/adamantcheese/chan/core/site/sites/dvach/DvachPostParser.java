package com.github.adamantcheese.chan.core.site.sites.dvach;

import android.text.Spanned;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.common.vichan.VichanPostParser;
import com.github.adamantcheese.chan.core.site.parser.comment_action.ChanCommentAction;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlNodeTreeAction;
import com.github.adamantcheese.chan.features.html_styling.impl.HtmlTagAction;
import com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;

public class DvachPostParser
        extends VichanPostParser {

    public DvachPostParser(ChanCommentAction elementAction) {
        super(elementAction);
    }

    @Override
    public Post parse(Post.Builder builder, @NonNull Theme theme, Callback callback) {
        CharSequence nameStyled = new HtmlNodeTreeAction(
                new HtmlTagAction(true),
                CommonStyleActions.getDefaultTextStylingAction(theme)
        ).style(builder.getName(), null);
        if (nameStyled instanceof Spanned) {
            Spanned spanned = (Spanned) nameStyled;
            ForegroundColorSpanHashed[] idColorSpan =
                    spanned.getSpans(0, nameStyled.length(), ForegroundColorSpanHashed.class);
            if (idColorSpan.length > 0) {
                builder.idColor = idColorSpan[0].getForegroundColor();
            }
        }
        String[] nameIdSplit = nameStyled.toString().replace('\u00A0', '\u0020').split("ID:");
        builder.name(nameIdSplit[0].trim());
        if (nameIdSplit.length > 1) {
            builder.posterId = nameIdSplit[1].trim();
        }
        return super.parse(builder, theme, callback);
    }
}
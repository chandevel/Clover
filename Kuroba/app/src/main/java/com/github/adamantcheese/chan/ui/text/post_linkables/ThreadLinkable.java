package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ThreadLink;
import com.github.adamantcheese.chan.ui.theme.Theme;

public class ThreadLinkable
        extends PostLinkable<ThreadLink> {
    public ThreadLinkable(
            @NonNull Theme theme, ThreadLink value
    ) {
        super(theme, value);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.setColor(quoteColor);
        textPaint.setUnderlineText(true);
    }
}

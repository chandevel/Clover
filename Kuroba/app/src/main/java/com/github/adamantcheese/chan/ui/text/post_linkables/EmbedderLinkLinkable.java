package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.theme.Theme;

/**
 * value is a URL
 */
public class EmbedderLinkLinkable
        extends PostLinkable<String> {
    public EmbedderLinkLinkable(
            @NonNull Theme theme, String value
    ) {
        super(theme, value);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.setColor(textPaint.linkColor);
        textPaint.setUnderlineText(true);
    }
}

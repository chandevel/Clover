package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.theme.Theme;

public class ParserLinkLinkable
        extends PostLinkable<String> {
    public ParserLinkLinkable(
            @NonNull Theme theme, String value
    ) {
        super(theme, value);
    }

    public boolean isJavascript() {
        return value.startsWith("javascript:");
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.setColor(textPaint.linkColor);
        textPaint.setUnderlineText(true);
    }
}

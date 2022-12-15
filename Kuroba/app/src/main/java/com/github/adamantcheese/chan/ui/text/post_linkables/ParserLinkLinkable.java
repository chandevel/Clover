package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.features.html_styling.base.StyleActionTextAdjuster;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.net.URLDecoder;

/**
 * value is a URL, generally the same as the spanned text
 */
public class ParserLinkLinkable
        extends PostLinkable<String>
        implements StyleActionTextAdjuster {
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

    @Override
    public CharSequence adjust(CharSequence base) {
        try {
            return URLDecoder.decode(base.toString(), "UTF-8");
        } catch (Exception ignored) {
            return base;
        }
    }
}

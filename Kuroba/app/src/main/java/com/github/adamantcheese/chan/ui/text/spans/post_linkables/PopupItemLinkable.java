package com.github.adamantcheese.chan.ui.text.spans.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.features.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;

/**
 * value is always just a bare Object
 */
public class PopupItemLinkable
        extends PostLinkable<Object> {
    private final int inlineQuoteColor;

    public PopupItemLinkable(@NonNull Theme theme) {
        this(theme, new Object());
    }

    private PopupItemLinkable(
            @NonNull Theme theme, Object value
    ) {
        super(theme, value);
        inlineQuoteColor = AndroidUtils.getThemeAttrColor(theme, R.attr.post_inline_quote_color);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.setColor(inlineQuoteColor);
        textPaint.setUnderlineText(true);
    }
}

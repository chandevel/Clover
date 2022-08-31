package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;

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

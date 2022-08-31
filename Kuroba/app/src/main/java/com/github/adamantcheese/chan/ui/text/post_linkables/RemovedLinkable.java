package com.github.adamantcheese.chan.ui.text.post_linkables;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;

import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.theme.Theme;

public class RemovedLinkable
        extends PostLinkable<Object> {
    public RemovedLinkable(@NonNull Theme theme) {
        this(theme, new Object());
    }

    private RemovedLinkable(
            @NonNull Theme theme, Object value
    ) {
        super(theme, value);
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.setStrikeThruText(true);
        textPaint.setColor(quoteColor);
        textPaint.setUnderlineText(true);
    }

    @Override
    public void onClick(@NonNull View widget) {
        showToast(widget.getContext(), "This post has been removed.");
    }
}

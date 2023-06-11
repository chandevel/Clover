package com.github.adamantcheese.chan.ui.text.spans.post_linkables;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;

import android.text.TextPaint;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.features.theme.Theme;

/**
 * value is the matching filter pattern
 */
public class FilterDebugLinkable
        extends PostLinkable<String> {

    private final int accentColor;

    public FilterDebugLinkable(@NonNull Theme theme, String value) {
        super(theme, value);
        accentColor = theme.accentColorInt;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.bgColor = accentColor;
        textPaint.setColor(getContrastColor(accentColor));
    }

    @Override
    public void onClick(@NonNull View widget) {
        showToast(widget.getContext(), "Matching filter: " + value, Toast.LENGTH_LONG);
    }
}

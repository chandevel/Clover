package com.github.adamantcheese.chan.ui.text.post_linkables;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;

import android.text.TextPaint;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;

public class FilterDebugLinkable
        extends PostLinkable<String> {

    private final int accentColor;

    public FilterDebugLinkable(@NonNull Theme theme, String value) {
        super(theme, value);
        accentColor = AndroidUtils.getAttrColor(theme.accentColor.accentStyleId, R.attr.colorAccent);
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

package com.github.adamantcheese.chan.ui.text.spans.post_linkables;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;

import android.text.TextPaint;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import java.util.Objects;

/**
 * value is the spoilered text
 */
public class SpoilerLinkable
        extends PostLinkable<CharSequence> {

    private final int spoilerColor;
    private boolean spoilerVisible;

    public SpoilerLinkable(
            @NonNull Theme theme, CharSequence value
    ) {
        super(theme, value);
        spoilerColor = AndroidUtils.getThemeAttrColor(theme, R.attr.post_spoiler_color);
        spoilerVisible = ChanSettings.revealTextSpoilers.get();
    }

    public boolean isSpoilerVisible() {
        return spoilerVisible;
    }

    @Override
    public void onClick(@NonNull View widget) {
        spoilerVisible = !spoilerVisible;
    }

    @Override
    public void updateDrawState(@NonNull TextPaint textPaint) {
        textPaint.bgColor = spoilerColor;
        textPaint.setColor(spoilerVisible ? getContrastColor(spoilerColor) : spoilerColor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostLinkable)) return false;
        PostLinkable<?> that = (PostLinkable<?>) o;
        return value.toString().equals(that.value.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.toString());
    }
}

package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.TextPaint;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.ui.theme.Theme;

/**
 * The Object can be a ThreadLink or a ResolveLink.
 */
public class ArchiveLinkable extends PostLinkable<Object>{
    public ArchiveLinkable(
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
}

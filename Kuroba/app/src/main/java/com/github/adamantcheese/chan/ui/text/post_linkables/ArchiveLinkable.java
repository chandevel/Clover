package com.github.adamantcheese.chan.ui.text.post_linkables;

import android.text.TextPaint;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ResolveLink;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ThreadLink;
import com.github.adamantcheese.chan.features.html_styling.base.StyleActionTextAdjuster;
import com.github.adamantcheese.chan.ui.theme.Theme;

import okhttp3.HttpUrl;

/**
 * The Object can be a ThreadLink or a ResolveLink.
 */
public class ArchiveLinkable
        extends PostLinkable<Object>
        implements StyleActionTextAdjuster {
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

    @Override
    public CharSequence adjust(CharSequence base) {
        try {
            HttpUrl.get(base.toString());
            if (value instanceof ThreadLink) {
                ThreadLink archiveData = (ThreadLink) value;
                return ">>" + (archiveData.postId == -1 ? archiveData.threadId : archiveData.postId) + " →";
            }
        } catch (Exception ignored) {}
        if ((value instanceof ThreadLink && ((ThreadLink) value).postId == -1) || value instanceof ResolveLink) {
            return TextUtils.concat(base, " →");
        }
        return base;
    }
}

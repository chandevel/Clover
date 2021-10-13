package com.github.adamantcheese.chan.core.site.parser.style;

import android.text.Spanned;
import android.text.SpannedString;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Node;

/**
 * Allows the ability to change the text or style a given element.
 */
public interface StyleAction {
    /**
     * @param node  The node that is currently being worked on.
     * @param text     The text to be styled, with all child nodes processed.
     * @param theme    Current theme for where this will be displayed.
     * @param post     The post that the final text will be in.
     * @param callback For getting info about this post in relation to the thread it is in.
     * @return stylized text for this node; can be adding styling to the passed in child inner text or replacing it
     */
    @NonNull
    SpannedString style(
            @NonNull Node node,
            @NonNull Spanned text,
            @NonNull Theme theme,
            @NonNull Post.Builder post,
            @NonNull PostParser.Callback callback
    );
}

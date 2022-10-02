package com.github.adamantcheese.chan.features.html_styling.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.github.adamantcheese.chan.utils.StringUtils.ReplacementGenerator;
import com.github.adamantcheese.chan.utils.StringUtils.TargetGenerator;

import org.jsoup.nodes.Node;

import java.util.Map;

public class TextReplacementStyleAction
        implements StyleAction {
    private final TargetGenerator targetGenerator;
    private final ReplacementGenerator replacementGenerator;

    public TextReplacementStyleAction(TargetGenerator targetGenerator, ReplacementGenerator replacementGenerator) {
        this.targetGenerator = targetGenerator;
        this.replacementGenerator = replacementGenerator;
    }

    @NonNull
    @Override
    public final CharSequence style(
            @NonNull Node node, @Nullable CharSequence text
    ) {
        return StringUtils.replaceAll(text, targetGenerator, replacementGenerator, false);
    }
}

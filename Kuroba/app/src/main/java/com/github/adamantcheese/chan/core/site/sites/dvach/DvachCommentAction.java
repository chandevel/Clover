package com.github.adamantcheese.chan.core.site.sites.dvach;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.STRIKETHROUGH;
import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.UNDERLINE;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentAction;

public class DvachCommentAction
        extends VichanCommentAction {
    public DvachCommentAction() {
        super();
        mapTagToRule("span", "s", STRIKETHROUGH);
        mapTagToRule("span", "u", UNDERLINE);
    }
}

package com.github.adamantcheese.chan.core.site.sites.dvach;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentAction;

import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.STRIKETHROUGH;
import static com.github.adamantcheese.chan.core.site.parser.style.CommonStyleActions.UNDERLINE;

public class DvachCommentAction
        extends VichanCommentAction {
    public DvachCommentAction() {
        super();
        mapTagToRule("span", "s", STRIKETHROUGH);
        mapTagToRule("span", "u", UNDERLINE);
    }
}

package com.github.adamantcheese.chan.core.site.sites.dvach;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;

import static com.github.adamantcheese.chan.core.site.parser.StyleRule.STRIKETHROUGH;
import static com.github.adamantcheese.chan.core.site.parser.StyleRule.UNDERLINE;

public class DvachCommentParser
        extends VichanCommentParser {
    public DvachCommentParser() {
        super();
        mapTagToRule("span", "s", STRIKETHROUGH);
        mapTagToRule("span", "u", UNDERLINE);
    }
}

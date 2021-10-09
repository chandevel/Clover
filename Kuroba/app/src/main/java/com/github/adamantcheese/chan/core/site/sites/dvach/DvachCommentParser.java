package com.github.adamantcheese.chan.core.site.sites.dvach;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.parser.StyleRule;

import static com.github.adamantcheese.chan.core.site.parser.StyleRule.STRIKETHROUGH;
import static com.github.adamantcheese.chan.core.site.parser.StyleRule.UNDERLINE;

public class DvachCommentParser
        extends VichanCommentParser {
    @Override
    public DvachCommentParser addDefaultRules() {
        super.addDefaultRules();
        rule(new StyleRule("span").cssClass("s").style(STRIKETHROUGH));
        rule(new StyleRule("span").cssClass("u").style(UNDERLINE));
        return this;
    }
}

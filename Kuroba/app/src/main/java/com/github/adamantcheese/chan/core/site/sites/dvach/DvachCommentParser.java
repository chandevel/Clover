package com.github.adamantcheese.chan.core.site.sites.dvach;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.parser.StyleRule;

public class DvachCommentParser
        extends VichanCommentParser {
    @Override
    public void addDefaultRules() {
        super.addDefaultRules();
        rule(StyleRule.tagRule("span").cssClass("s").strikeThrough());
        rule(StyleRule.tagRule("span").cssClass("u").underline());
    }
}

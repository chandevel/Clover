package com.github.adamantcheese.chan.core.site.parser;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;

public class DvachCommentParser extends VichanCommentParser {
    @Override
    public void addDefaultRules() {
        super.addDefaultRules();
        rule(StyleRule.tagRule("span").cssClass("s").strikeThrough());
        rule(StyleRule.tagRule("span").cssClass("u").underline());
    }
}

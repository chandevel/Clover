package org.floens.chan.core.site.sites.dvach;

import org.floens.chan.core.site.common.vichan.VichanCommentParser;
import org.floens.chan.core.site.parser.StyleRule;

public class DvachCommentParser extends VichanCommentParser {
    @Override
    public void addDefaultRules() {
        rule(StyleRule.tagRule("span").cssClass("s").strikeThrough());
        rule(StyleRule.tagRule("span").cssClass("u").underLine());
        super.addDefaultRules();
    }
}

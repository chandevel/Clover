package com.github.adamantcheese.chan.core.site.sites.dvach;

import android.content.Context;

import com.github.adamantcheese.chan.core.site.common.vichan.VichanCommentParser;
import com.github.adamantcheese.chan.core.site.parser.StyleRule;

public class DvachCommentParser
        extends VichanCommentParser {
    public DvachCommentParser(Context context) {
        super(context);
    }

    @Override
    public DvachCommentParser addDefaultRules() {
        super.addDefaultRules();
        rule(StyleRule.tagRule("span").cssClass("s").strikeThrough());
        rule(StyleRule.tagRule("span").cssClass("u").underline());
        return this;
    }
}

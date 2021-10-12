package com.github.adamantcheese.chan.core.site.common.vichan;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;

public class VichanPostParser
        extends PostParser {
    @Override
    public String createQuoteElementString(Post.Builder post) {
        return "<a href=\"/" + post.board.code + "/res/" + post.opId + ".html#$1\">&gt;&gt;$1</a>";
    }
}

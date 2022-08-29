package com.github.adamantcheese.chan.core.site.common.vichan;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.parser.comment_action.ChanCommentAction;

public class VichanPostParser
        extends PostParser {

    public VichanPostParser(ChanCommentAction elementAction) {
        super(elementAction);
    }

    @Override
    public String createQuoteElementString(Post.Builder post) {
        return "<a href=\"/" + post.board.code + "/res/" + post.opId + ".html#$1\">&gt;&gt;$1</a>";
    }
}

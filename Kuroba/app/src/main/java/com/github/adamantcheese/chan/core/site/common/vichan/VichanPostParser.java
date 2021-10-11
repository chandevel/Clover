package com.github.adamantcheese.chan.core.site.common.vichan;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.core.site.parser.style.comment.ChanCommentAction;

public class VichanPostParser
        extends PostParser {
    /**
     * Construct a new post parser, with the given action for styling parsed comments.
     *
     * @param chanCommentAction The action that describes how to style post comments.
     *                          A generic ChanCommentAction is generally fine.
     */
    public VichanPostParser(ChanCommentAction chanCommentAction) {
        super(chanCommentAction);
    }

    @Override
    public String createQuoteElementString(Post.Builder post) {
        return "<a href=\"/" + post.board.code + "/res/" + post.opId + ".html#$1\">&gt;&gt;$1</a>";
    }
}

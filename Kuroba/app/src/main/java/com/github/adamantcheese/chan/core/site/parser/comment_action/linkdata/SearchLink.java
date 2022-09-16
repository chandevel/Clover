package com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata;

import java.util.Objects;

public class SearchLink {
    public String board;
    public String search;

    public SearchLink(String board, String search) {
        this.board = board;
        this.search = search;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SearchLink)) return false;
        SearchLink that = (SearchLink) o;
        return board.equals(that.board) && search.equals(that.search);
    }

    @Override
    public int hashCode() {
        return Objects.hash(board, search);
    }
}

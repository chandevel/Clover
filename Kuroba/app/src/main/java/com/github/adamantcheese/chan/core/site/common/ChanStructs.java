package com.github.adamantcheese.chan.core.site.common;

import com.github.adamantcheese.chan.core.model.orm.Board;

import java.util.List;

public class ChanStructs {
    public static class Boards {
        public final List<Board> boards;

        public Boards(List<Board> boards) {
            this.boards = boards;
        }
    }
}

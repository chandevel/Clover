package org.floens.chan.core.site;

import org.floens.chan.core.model.orm.Board;

import java.util.List;

public class Boards {
    public final List<Board> boards;

    public Boards(List<Board> boards) {
        this.boards = boards;
    }
}

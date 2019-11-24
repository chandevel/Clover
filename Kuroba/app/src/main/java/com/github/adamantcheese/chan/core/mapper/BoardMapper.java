package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.save.SerializableBoard;

public class BoardMapper {

    public static SerializableBoard toSerializableBoard(Board board) {
        return new SerializableBoard(board.id,
                                     board.siteId,
                                     SiteMapper.toSerializableSite(board.site),
                                     board.saved,
                                     board.order,
                                     board.name,
                                     board.code
        );
    }
}

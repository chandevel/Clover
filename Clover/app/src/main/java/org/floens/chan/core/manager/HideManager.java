package org.floens.chan.core.manager;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.model.Hide;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Allan on 2015-03-08.
 */
public class HideManager {
    private final Map<String, Set<Integer>> hiddenThreadsByBoard = new HashMap<String, Set<Integer>>();

    public HideManager() {
        updateHidden();
    }

    public void addHide(Post post) {
        Hide hide = new Hide(post.board, post.no);
        ChanApplication.getDatabaseManager().addHide(hide);
        addHideToCache(hide);
    }

    public boolean isHidden(Post post) {
        Set<Integer> boardHidden = hiddenThreadsByBoard.get(post.board);
        if (boardHidden == null) {
            return false;
        }
        return boardHidden.contains(post.no);
    }

    private void addHideToCache(Hide hide) {
        Set<Integer> boardHidden = hiddenThreadsByBoard.get(hide.board);
        if (boardHidden == null) {
            Set<Integer> boardHiddden = new HashSet<Integer>();
            hiddenThreadsByBoard.put(hide.board, boardHidden);
        }
        boardHidden.add(hide.no);
    }
    private void updateHidden() {
        hiddenThreadsByBoard.clear();
        for (Hide hide : ChanApplication.getDatabaseManager().getHidden()) {
            addHideToCache(hide);
        }
    }
}

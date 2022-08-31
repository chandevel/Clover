package com.github.adamantcheese.chan.core.site.common;

import com.github.adamantcheese.chan.core.model.orm.Board;

import java.util.ArrayList;
import java.util.List;

public class CommonDataStructs {
    public static class Boards
            extends ArrayList<Board> {
        public Boards(int size) {
            super(size);
        }

        public Boards(List<Board> boards) {
            super(boards);
        }

        public Boards() {
            super();
        }
    }

    public static class ChanPages
            extends ArrayList<ChanPage> {}

    public static class ChanPage {
        public final int page;
        public final List<Integer> threadNumbers;

        public ChanPage(int page, List<Integer> threads) {
            this.page = page;
            this.threadNumbers = threads;
        }
    }

    public enum CaptchaType {
        V2JS,
        V2NOJS,
        CHAN4_CUSTOM;
    }
}

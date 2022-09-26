package com.github.adamantcheese.chan.core.site.common;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;

import java.util.*;

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

    public static class Filters
            extends ArrayList<Filter> {
        public Filters(Filter... filters) {
            this(Arrays.asList(filters));
        }

        public Filters(List<Filter> filters) {
            super(filters);
        }

        public Filters() {
            super();
        }

        public void setAllEnableState(boolean enable) {
            for (Filter f : this) {
                f.enabled = enable;
            }
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
        CHAN4_CUSTOM
    }
}

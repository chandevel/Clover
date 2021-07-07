package com.github.adamantcheese.chan.core.site.common;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.settings.primitives.OptionSettingItem;

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
        public final List<ThreadNoTimeModPair> threads;

        public ChanPage(int page, List<ThreadNoTimeModPair> threads) {
            this.page = page;
            this.threads = threads;
        }
    }

    public static class ThreadNoTimeModPair {
        public final int no;
        public final long modified;

        public ThreadNoTimeModPair(int no, long modified) {
            this.no = no;
            this.modified = modified;
        }
    }

    public enum CaptchaType
            implements OptionSettingItem {
        V2JS,
        V2NOJS,
        CUSTOM;

        @Override
        public String getKey() {
            return name().toLowerCase();
        }
    }
}

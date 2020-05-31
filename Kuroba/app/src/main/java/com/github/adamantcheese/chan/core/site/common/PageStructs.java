package com.github.adamantcheese.chan.core.site.common;

import java.util.List;

public class PageStructs {
    public static class ChanPages {
        public final List<ChanPage> pages;

        public ChanPages(List<ChanPage> pages) {
            this.pages = pages;
        }
    }

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
}


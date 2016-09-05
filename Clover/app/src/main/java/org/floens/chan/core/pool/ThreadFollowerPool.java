package org.floens.chan.core.pool;

import org.floens.chan.core.model.Loadable;

import java.util.ArrayDeque;
import java.util.Deque;

public class ThreadFollowerPool {
    Deque<Loadable> pool;

    public ThreadFollowerPool() {
        pool = new ArrayDeque<>();
    }

    public void put(Loadable threadLoadable) {
        pool.addFirst(threadLoadable);
    }

    public Loadable get() {
        return pool.removeFirst();
    }

    public boolean empty() {
        return pool.isEmpty();
    }

    public interface Callback {
        void threadCrossLinkOpen(Loadable threadLoadable);

        boolean threadBackPressed();
    }
}

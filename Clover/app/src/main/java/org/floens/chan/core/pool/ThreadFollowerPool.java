package org.floens.chan.core.pool;

import org.floens.chan.core.model.Loadable;
import java.util.Stack;

public class ThreadFollowerPool {

    Stack<Loadable> pool;

    public ThreadFollowerPool() {
        pool = new Stack<>();
    }

    public void put(Loadable threadLoadable) {
        pool.push(threadLoadable);
    }

    public Loadable get() {
        return pool.pop();
    }

    public boolean empty() {
        return pool.empty();
    }

    public interface Callback {
        void threadCrossLinkOpen(Loadable threadLoadable);

        boolean threadBackPressed();
    }
}

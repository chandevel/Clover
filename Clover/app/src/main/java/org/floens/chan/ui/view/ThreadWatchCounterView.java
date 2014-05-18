/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.view;

import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.ui.adapter.PostAdapter;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

public class ThreadWatchCounterView extends TextView implements View.OnClickListener {
    private boolean detached = false;
    private ThreadManager tm;
    private PostAdapter ad;

    public ThreadWatchCounterView(Context activity) {
        super(activity);
    }

    public ThreadWatchCounterView(Context activity, AttributeSet attbs) {
        super(activity, attbs);
    }

    public ThreadWatchCounterView(Context activity, AttributeSet attbs, int style) {
        super(activity, attbs, style);
    }

    public void init(final ThreadManager threadManager, final ListView listView, final PostAdapter adapter) {
        tm = threadManager;
        ad = adapter;

        updateCounterText(threadManager);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!detached) {
                    adapter.notifyDataSetChanged();
                }
            }
        }, 1000);

        setOnClickListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        setOnClickListener(null);

        detached = true;
    }

    @Override
    public void onClick(View v) {
        Loader loader = tm.getLoader();
        if (loader != null) {
            loader.requestMoreDataAndResetTimer();
        }

        ad.notifyDataSetChanged();
    }

    private void updateCounterText(ThreadManager threadManager) {
        Loader loader = tm.getLoader();
        if (loader == null)
            return;

        int time = Math.round(loader.getTimeUntilLoadMore() / 1000f);

        String error = ad.getErrorMessage();
        if (error != null) {
            setText(error);
        } else {
            if (time <= 0) {
                setText("Loading");
            } else {
                setText("Loading in " + time);
            }
        }
    }
}

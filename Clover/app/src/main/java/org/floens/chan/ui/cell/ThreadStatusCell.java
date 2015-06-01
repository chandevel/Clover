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
package org.floens.chan.ui.cell;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Post;

import static org.floens.chan.utils.AndroidUtils.ROBOTO_MEDIUM;

public class ThreadStatusCell extends LinearLayout implements View.OnClickListener {
    private static final int UPDATE_INTERVAL = 1000;
    private static final int MESSAGE_INVALIDATE = 1;

    private Callback callback;

    private boolean running = false;

    private TextView text;
    private String error;
    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == MESSAGE_INVALIDATE) {
                if (running) {
                    schedule();
                }

                update();
                return true;
            } else {
                return false;
            }
        }
    });

    public ThreadStatusCell(Context context, AttributeSet attrs) {
        super(context, attrs);

        setBackgroundResource(R.drawable.item_background);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        text = (TextView) findViewById(R.id.text);
        text.setTypeface(ROBOTO_MEDIUM);

        setOnClickListener(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void update() {
        if (error != null) {
            text.setText(error);
        } else {
            ChanThread chanThread = callback.getChanThread();
            if (chanThread == null) {
                return; // Recyclerview not clearing immediately or view didn't receive onDetachedFromWindow
            }
            String statusText = "";

            if (chanThread.archived) {
                statusText += getContext().getString(R.string.thread_archived) + "\n";
            } else if (chanThread.closed) {
                statusText += getContext().getString(R.string.thread_closed) + "\n";
            }

            if (!chanThread.archived && !chanThread.closed) {
                long time = callback.getTimeUntilLoadMore() / 1000L;
                if (!callback.isWatching()) {
                    statusText += getContext().getString(R.string.thread_refresh_bar_inactive) + "\n";
                } else if (time <= 0) {
                    statusText += getContext().getString(R.string.thread_refresh_now) + "\n";
                } else {
                    statusText += getContext().getString(R.string.thread_refresh_countdown, time) + "\n";
                }
            }

            Post op = chanThread.op;
            statusText += getContext().getString(R.string.thread_stats, op.replies, op.images, op.uniqueIps);

            text.setText(statusText);
        }
    }

    private void schedule() {
        running = true;
        Message message = handler.obtainMessage(1);
        if (!handler.hasMessages(MESSAGE_INVALIDATE)) {
            handler.sendMessageDelayed(message, UPDATE_INTERVAL);
        }
    }

    private void unschedule() {
        running = false;
        handler.removeMessages(MESSAGE_INVALIDATE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unschedule();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            update();
            schedule();
        } else {
            unschedule();
        }
    }

    @Override
    public void onClick(View v) {
        callback.onListStatusClicked();
        update();
    }

    public interface Callback {
        long getTimeUntilLoadMore();

        boolean isWatching();

        ChanThread getChanThread();

        void onListStatusClicked();
    }
}

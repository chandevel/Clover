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
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.core.model.Board;
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
                if (running && update()) {
                    schedule();
                }

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
        if (error == null) {
            schedule();
        }
    }

    public boolean update() {
        if (error != null) {
            text.setText(error + "\n" + getContext().getString(R.string.thread_refresh_bar_inactive));
            return false;
        } else {
            ChanThread chanThread = callback.getChanThread();
            if (chanThread == null) {
                return false; // Recyclerview not clearing immediately or view didn't receive onDetachedFromWindow
            }

            boolean update = false;

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
                update = true;
            }

            Post op = chanThread.op;

            Board board = op.board;
            if (board != null) {
                SpannableString replies = new SpannableString(op.replies + "R");
                if (op.replies >= board.bumpLimit) {
                    replies.setSpan(new StyleSpan(Typeface.ITALIC), 0, replies.length(), 0);
                }
                SpannableString images = new SpannableString(op.images + "I");
                if (op.images >= board.imageLimit) {
                    images.setSpan(new StyleSpan(Typeface.ITALIC), 0, images.length(), 0);
                }

                text.setText(TextUtils.concat(statusText, replies, " / ", images, " / ", String.valueOf(op.uniqueIps) + "P"));
            }

            return update;
        }
    }

    private void schedule() {
        running = true;
        if (!handler.hasMessages(MESSAGE_INVALIDATE)) {
            handler.sendMessageDelayed(handler.obtainMessage(MESSAGE_INVALIDATE), UPDATE_INTERVAL);
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
            if (update()) {
                schedule();
            }
        } else {
            unschedule();
        }
    }

    @Override
    public void onClick(View v) {
        error = null;
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

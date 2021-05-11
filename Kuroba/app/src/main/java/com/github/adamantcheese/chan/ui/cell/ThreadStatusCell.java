/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.ui.cell;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ThreadStatusCell
        extends LinearLayout
        implements View.OnClickListener {
    private static final int UPDATE_INTERVAL = 1000;
    private static final int MESSAGE_INVALIDATE = 1;

    private Callback callback;

    private boolean running = false;

    private TextView text;
    private String error;
    private final Handler handler = new Handler(msg -> {
        if (msg.what == MESSAGE_INVALIDATE) {
            if (running && update()) {
                schedule();
            }

            return true;
        } else {
            return false;
        }
    });

    public ThreadStatusCell(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (isInEditMode()) return;

        text = findViewById(R.id.text);
        text.setTypeface(ThemeHelper.getTheme().mainFont);

        setOnClickListener(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setError(String error) {
        this.error = error;
        if (error == null) {
            schedule();
        } else {
            update();
        }
    }

    @SuppressLint("SetTextI18n")
    public boolean update() {
        if (error != null) {
            text.setText(error + "\n" + getString(R.string.thread_refresh_bar_inactive));
            return false;
        } else {
            ChanThread chanThread = callback.getChanThread();
            if (chanThread == null) {
                // Recyclerview not clearing immediately or view didn't receive
                // onDetachedFromWindow.
                return false;
            }

            boolean update = false;

            SpannableStringBuilder builder = new SpannableStringBuilder();

            if (chanThread.isArchived()) {
                builder.append(getString(R.string.thread_archived));
            } else if (chanThread.isClosed()) {
                builder.append(getString(R.string.thread_closed));
            }

            if (!chanThread.isArchived() && !chanThread.isClosed()) {
                long time = callback.getTimeUntilLoadMore() / 1000L;
                if (!callback.isWatching()) {
                    builder.append(getString(R.string.thread_refresh_bar_inactive));
                } else if (time <= 0) {
                    builder.append(getString(R.string.loading));
                } else {
                    builder.append(getString(R.string.thread_refresh_countdown, time));
                }
                update = true;
            }

            builder.append('\n').append(chanThread.summarize(false));
            text.setText(builder);

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
        if (callback.getChanThread() != null) {
            callback.onListStatusClicked();
        }
        update();
    }

    public interface Callback {
        long getTimeUntilLoadMore();

        boolean isWatching();

        @Nullable
        ChanThread getChanThread();

        void onListStatusClicked();
    }
}

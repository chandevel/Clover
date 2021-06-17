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
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

@SuppressLint("AppCompatCustomView")
public class ThreadStatusCell
        extends TextView
        implements View.OnClickListener {
    private static final int UPDATE_INTERVAL = 1000;
    private static final int MESSAGE_INVALIDATE = 1;

    private Callback callback;

    private boolean running = false;

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

        if (!isInEditMode()) {
            setTypeface(ThemeHelper.getTheme().mainFont);
        } else {
            setCallback(new Callback() {
                @Override
                public long getTimeUntilLoadMore() {
                    return SECONDS.toMillis(20);
                }

                @Override
                public boolean isWatching() {
                    return true;
                }

                @Override
                public ChanThread getChanThread() {
                    List<Post> testPosts = new ArrayList<>();
                    testPosts.add(new Post.Builder().board(Board.getDummyBoard())
                            .no(1)
                            .opId(1)
                            .replies(1)
                            .images(1)
                            .uniqueIps(1)
                            .setUnixTimestampSeconds(System.currentTimeMillis())
                            .comment("")
                            .build());
                    return new ChanThread(Loadable.emptyLoadable(), testPosts);
                }

                @Override
                public void onListStatusClicked() {}
            });
            update();
        }

        setOnClickListener(this);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setError(String error) {
        this.error = error;
        update();
        if (error == null) {
            schedule();
        }
    }

    @SuppressLint("SetTextI18n")
    public boolean update() {
        if (error != null) {
            setText(error + "\n" + getContext().getString(R.string.thread_refresh_bar_inactive));
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
                builder.append(getContext().getString(R.string.thread_archived));
            } else if (chanThread.isClosed()) {
                builder.append(getContext().getString(R.string.thread_closed));
            }

            if (!chanThread.isArchived() && !chanThread.isClosed()) {
                long time = callback.getTimeUntilLoadMore() / 1000L;
                if (!callback.isWatching()) {
                    builder.append(getContext().getString(R.string.thread_refresh_bar_inactive));
                } else if (time <= 0) {
                    builder.append(getContext().getString(R.string.loading));
                } else {
                    builder.append(getContext().getString(R.string.thread_refresh_countdown, time));
                }
                update = true;
            }

            builder.append('\n').append(chanThread.summarize(false));
            setText(builder);

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
        callback.onListStatusClicked();
        setError(null);
    }

    public interface Callback {
        long getTimeUntilLoadMore();

        boolean isWatching();

        @Nullable
        ChanThread getChanThread();

        void onListStatusClicked();
    }
}

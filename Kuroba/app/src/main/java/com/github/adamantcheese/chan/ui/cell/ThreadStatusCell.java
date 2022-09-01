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

import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.List;

public class ThreadStatusCell
        extends AppCompatTextView {
    private Callback callback;

    private String error;

    public ThreadStatusCell(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (isInEditMode()) {
            setCallback(new EditModeCallback());
            update();
        }

        setOnClickListener(v -> {
            callback.onListStatusClicked();
            setError(null);
        });
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setError(String error) {
        this.error = error;
        update();
    }

    @SuppressLint("SetTextI18n")
    public void update() {
        if (error != null) {
            setText(error + "\n" + getContext().getString(R.string.thread_refresh_bar_inactive));
        } else {
            ChanThread chanThread = callback.getChanThread();
            if (chanThread == null) {
                // Recyclerview not clearing immediately or view didn't receive onDetachedFromWindow.
                return;
            }

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
            }

            builder.append('\n').append(chanThread.summarize(false));
            setText(builder);
        }
    }

    @Subscribe
    public void onSystemTick(String tick) {
        update();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        EventBus.getDefault().unregister(this);
    }

    public interface Callback {
        long getTimeUntilLoadMore();

        boolean isWatching();

        @Nullable
        ChanThread getChanThread();

        void onListStatusClicked();
    }

    public class EditModeCallback
            implements Callback {
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
            testPosts.add(new Post.Builder()
                    .board(Board.getDummyBoard())
                    .no(1)
                    .opId(1)
                    .replies(1)
                    .images(1)
                    .uniqueIps(1)
                    .setUnixTimestampSeconds(System.currentTimeMillis())
                    .comment("")
                    .build());
            return new ChanThread(Loadable.dummyLoadable(), testPosts);
        }

        @Override
        public void onListStatusClicked() {}
    }
}

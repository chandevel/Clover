package org.floens.chan.ui.cell;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Post;

import static org.floens.chan.utils.AndroidUtils.getAttrDrawable;

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setBackground(getAttrDrawable(context, android.R.attr.selectableItemBackground));
        } else {
            setBackgroundResource(R.drawable.gray_background_selector);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        text = (TextView) findViewById(R.id.text);

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

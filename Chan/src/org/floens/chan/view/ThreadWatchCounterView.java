package org.floens.chan.view;

import org.floens.chan.manager.ThreadManager;
import org.floens.chan.watch.WatchLogic;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

public class ThreadWatchCounterView extends TextView {
    private boolean detached = false;
    
    public ThreadWatchCounterView(Context activity) {
        super(activity);
    }
    
    public ThreadWatchCounterView(Context activity, AttributeSet attbs) {
        super(activity, attbs);
    }
    
    public ThreadWatchCounterView(Context activity, AttributeSet attbs, int style) {
        super(activity, attbs, style);
    }
    
    public void init(final ThreadManager threadManager, final ListView listView) {
        updateCounterText(threadManager);
        
        postInvalidateDelayed(1000);
        
        postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!detached) {
                    updateCounterText(threadManager);
                    // TODO: This sometimes fails to recreate this view
                    listView.invalidateViews();
                }
            }
        }, 1000);
        
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                threadManager.loadMore();
            }
        });
    }
    
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
        setOnClickListener(null);
        
        detached = true;
    }
    
    private void updateCounterText(ThreadManager threadManager) {
        WatchLogic logic = threadManager.getWatchLogic();
        
        if (logic != null) {
            int time = Math.round(logic.timeLeft() / 1000f);
            
            if (time <= 0) {
                setText("Loading");
            } else {
                setText("Loading in " + time);
            }
        }
    }
}





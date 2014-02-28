package org.floens.chan.view;

import org.floens.chan.manager.ThreadManager;
import org.floens.chan.watch.WatchLogic;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ThreadWatchCounterView extends TextView implements View.OnClickListener {
    private boolean detached = false;
    private ThreadManager tm;
    
    public ThreadWatchCounterView(Context activity) {
        super(activity);
    }
    
    public ThreadWatchCounterView(Context activity, AttributeSet attbs) {
        super(activity, attbs);
    }
    
    public ThreadWatchCounterView(Context activity, AttributeSet attbs, int style) {
        super(activity, attbs, style);
    }
    
    public void init(final ThreadManager threadManager, final ListView listView, final BaseAdapter adapter) {
        tm = threadManager;
        
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
        tm.loadMore();
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





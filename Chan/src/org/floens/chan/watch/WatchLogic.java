package org.floens.chan.watch;

import java.util.Timer;
import java.util.TimerTask;

import org.floens.chan.utils.Logger;

public class WatchLogic {
    private static final String TAG = "WatchLogic";
    
    private static final int[] timeouts = {10, 15, 20, 30, 60, 90, 120, 180, 240, 300};
    
    private WatchListener listener;
    private int selectedTimeout;
    private long lastLoadTime;
    private int lastPostCount;
    
    private Timer timer = new Timer();
    
    public WatchLogic(WatchListener listener) {
        this.listener = listener;
    }
    
    /**
     * Stops the timer and removes the listener
     */
    public void destroy() {
        Logger.d(TAG, "Destroy");
        this.listener = null;
        clearTimer();
    }
    
    /**
     * Starts the timer from the beginning
     */
    public void startTimer() {
        Logger.d(TAG, "Start timer");
        lastLoadTime = now();
        selectedTimeout = 0;
        
        scheduleTimer();
    }
    
    /**
     * Stops the timer
     */
    public void stopTimer() {
        Logger.d(TAG, "Stop timer");
        
        clearTimer();
    }
    
    /**
     * Call onWatchReloadRequested on the listener and reset the timer
     */
    public void loadNow() {
        clearTimer();
        lastLoadTime = 0;
        selectedTimeout = -1; // so that the next timeout will be the first one
        
        if (listener != null) {
            listener.onWatchReloadRequested();
        }
    }
    
    /**
     * Call this to notify of new posts.
     * @param postCount how many posts there were loaded
     */
    public void onLoaded(int postCount) {
        Logger.d(TAG, "Loaded. Post count " + postCount + ", last " + lastPostCount);
        
        if (postCount > lastPostCount) {
            selectedTimeout = 0;
        } else {
            selectedTimeout = Math.min(timeouts.length - 1, selectedTimeout + 1);
        }
        
        lastLoadTime = now();
        lastPostCount = postCount;
        
        scheduleTimer();
    }
    
    /**
     * Time in ms left before a reload is necessary.
     * @return
     */
    public long timeLeft() {
        long waitTime = timeouts[Math.max(0, selectedTimeout)] * 1000L;
        return lastLoadTime + waitTime - now();
    }
    
    private void scheduleTimer() {
        Logger.d(TAG, "Scheduling timer");
        
        clearTimer();
        
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onWatchReloadRequested();
                }
            }
        }, Math.max(0, timeLeft()));
    }
    
    /**
     * Clear all the scheduled runnables
     */
    private void clearTimer() {
        Logger.d(TAG, "Clearing timer");
        
        timer.cancel();
        timer.purge();
        timer = new Timer();
    }
    
    private long now() {
        return System.currentTimeMillis();
    }
    
    public interface WatchListener {
        public void onWatchReloadRequested();
    }
}



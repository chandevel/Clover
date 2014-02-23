package org.floens.chan.service;

import java.util.ArrayList;

import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;

public class PinnedService /*extends Service*/ {
    /**
     * Base interval when the thread wakes up
     */
    private final int LOAD_BASE_INTERVAL = 20000;
    
    private PinnedManager pinnedManager;
    private Thread loadThread;
    private final boolean running = true;
    
    private final ArrayList<Pin> pinList = new ArrayList<Pin>();
    
    private final Runnable loadRunnable = new Runnable() {
        @Override
        public void run() {
            while (running) {
//                loadPins();
                
                try {
                    Thread.sleep(LOAD_BASE_INTERVAL);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    
    /*public PinnedService() {
        pinnedManager = ChanApplication.getPinnedManager();
    }
    
    private void loadPins() {
        organizePins();
        
        for (Pin pin : pinList) {
            loadPin(pin);
        }
    }
    
    private void loadPin(Pin pin) {
        if (!pin.threadLoader.isLoading()) {
            pin.startLoading();
        }
    }
    
    /**
     * Add not yet added pins to our list.
     * Remove old/unwatched pins from our list.
     
    private void organizePins() {
        ArrayList<Pin> managerList = pinnedManager.getPinnedThreads();
        
        for (Pin pin : managerList) {
            if (pin.getShouldWatch() && !pinList.contains(pin)) {
                // Add pin to watcher
                pinList.add(pin);
                
                testToast("Added pin: " + pin.loadable.title);
            }
        }
        
        for (Iterator<Pin> it = pinList.iterator(); it.hasNext();) {
            Pin pin = it.next();
            if (!pin.getShouldWatch() || !managerList.contains(pin)) {
                // Remove pin from watcher
                it.remove();
                
                testToast("Removed pin: " + pin.loadable.title);
            }
        }
    }
    
    private void testToast(final String text) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(PinnedService.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        testToast("Service started!");
        
        if (loadThread == null) {
            loadThread = new Thread(loadRunnable);
//            loadThread.start();
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        testToast("Service stopped!");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }*/

}

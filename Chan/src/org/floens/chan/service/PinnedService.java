package org.floens.chan.service;

import java.util.List;

import org.floens.chan.R;
import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class PinnedService extends Service {
    private Thread loadThread;
    private boolean running = true;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        start();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        
        running = false;
        
        showNotification("Stop");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void start() {
        showNotification("Start");
        
        loadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    doUpdates();
                    
                    try {
                        Thread.sleep(60000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        
        loadThread.start();
    }
    
    private void doUpdates() {
        List<Pin> pins = PinnedManager.getInstance().getPins();
        for (Pin pin : pins) {
            pin.updateWatch();
//            pin.newPostCount++;
        }
    }
    
    public static void callOnPinsChanged() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                PinnedManager.getInstance().onPinsChanged();
            }
        });
    }
    
    @SuppressWarnings("deprecation")
    private void showNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        Notification.Builder builder = new Notification.Builder(this);
        builder.setTicker(text);
        builder.setContentTitle(text);
        builder.setContentText(text);
        builder.setSmallIcon(R.drawable.ic_stat_notify);
        
        nm.notify(1, builder.getNotification());
    }
}





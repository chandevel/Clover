package com.github.adamantcheese.chan.ui.captcha;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.utils.Logger;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

public class CaptchaHolder {
    private static final String TAG = "CaptchaHolder";
    private static final long INTERVAL = 5000;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private AtomicBoolean running = new AtomicBoolean(false);

    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private Timer timer;

    private Map<String, CaptchaValidationListener> listeners = new HashMap<>();

    @GuardedBy("itself")
    private final List<CaptchaInfo> captchaQueue = new ArrayList<>();

    public void addListener(String tag, CaptchaValidationListener listener) {
        mainThreadHandler.post(() -> {
            listeners.put(tag, listener);
            notifyListeners();
        });
    }

    public void removeListener(String tag) {
        mainThreadHandler.post(() -> listeners.remove(tag));
    }

    public void addNewToken(String token, long tokenLifetime) {
        removeNotValidTokens();

        synchronized (captchaQueue) {
            captchaQueue.add(0, new CaptchaInfo(token, tokenLifetime + System.currentTimeMillis()));
            Logger.d(TAG, "A new token has been added, validCount = " + captchaQueue.size() + ", token = " + token);
        }

        notifyListeners();
        startTimer();
    }

    public boolean hasToken() {
        removeNotValidTokens();

        synchronized (captchaQueue) {
            if (captchaQueue.isEmpty()) {
                stopTimer();
                return false;
            }
        }

        return true;
    }

    @Nullable
    public String getToken() {
        removeNotValidTokens();

        synchronized (captchaQueue) {
            if (captchaQueue.isEmpty()) {
                stopTimer();
                return null;
            }

            int lastIndex = captchaQueue.size() - 1;

            String token = captchaQueue.get(lastIndex).getToken();
            captchaQueue.remove(lastIndex);
            Logger.d(TAG, "getToken() token = " + token);

            notifyListeners();
            return token;
        }
    }

    private void startTimer() {
        if (running.compareAndSet(false, true)) {
            timer = new Timer();
            timer.scheduleAtFixedRate(new CheckCaptchaFreshnessTask(), INTERVAL, INTERVAL);

            Logger.d(TAG, "Timer started");
        }
    }

    private void stopTimer() {
        if (running.compareAndSet(true, false)) {
            timer.cancel();
            timer.purge();

            Logger.d(TAG, "Timer stopped");
        }
    }

    private void removeNotValidTokens() {
        long now = System.currentTimeMillis();
        boolean captchasCountDecreased = false;

        synchronized (captchaQueue) {
            ListIterator<CaptchaInfo> it = captchaQueue.listIterator(captchaQueue.size());
            while (it.hasPrevious()) {
                CaptchaInfo captchaInfo = it.previous();
                if (now > captchaInfo.getValidUntil()) {
                    captchasCountDecreased = true;
                    it.remove();

                    Logger.d(TAG, "Captcha token got expired, now = " + sdf.format(now) +
                            ", token validUntil = " + sdf.format(captchaInfo.getValidUntil())
                            + ", token = " + captchaInfo.getToken());
                }
            }

            if (captchaQueue.isEmpty()) {
                stopTimer();
            }
        }

        if (captchasCountDecreased) {
            notifyListeners();
        }
    }

    private void notifyListeners() {
        mainThreadHandler.post(() -> {
            int count = 0;

            synchronized (captchaQueue) {
                count = captchaQueue.size();
            }

            Logger.d(TAG, "notifyListeners() listeners count = " + listeners.size());

            for (CaptchaValidationListener listener : listeners.values()) {
                listener.onCaptchaCountChanged(count);
            }
        });
    }

    private class CheckCaptchaFreshnessTask extends TimerTask {
        @Override
        public void run() {
            removeNotValidTokens();
        }
    }

    public interface CaptchaValidationListener {
        void onCaptchaCountChanged(int validCaptchaCount);
    }

    private static class CaptchaInfo {
        private String token;
        private long validUntil;

        public CaptchaInfo(String token, long validUntil) {
            this.token = token;
            this.validUntil = validUntil;
        }

        public String getToken() {
            return token;
        }

        public long getValidUntil() {
            return validUntil;
        }

        @Override
        public int hashCode() {
            return token.hashCode() *
                    31 * (int) (validUntil & 0x00000000FFFFFFFFL) *
                    31 * (int) ((validUntil >> 32) & 0x00000000FFFFFFFFL);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (other == null) {
                return false;
            }

            if (this == other) {
                return true;
            }

            if (this.getClass() != other.getClass()) {
                return false;
            }

            CaptchaInfo otherCaptchaInfo = (CaptchaInfo) other;

            return token.equals(otherCaptchaInfo.token) &&
                    validUntil == otherCaptchaInfo.getValidUntil();
        }

        @NonNull
        @Override
        public String toString() {
            return "validUntil = " + sdf.format(validUntil) + ", token = " + token;
        }
    }
}

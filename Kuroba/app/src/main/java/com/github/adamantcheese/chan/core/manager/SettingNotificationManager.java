package com.github.adamantcheese.chan.core.manager;

import android.annotation.SuppressLint;

import androidx.annotation.ColorInt;

import com.github.adamantcheese.chan.R;

import org.greenrobot.eventbus.EventBus;

import java.util.BitSet;

public class SettingNotificationManager {
    public static void postNotification(SettingNotificationType notification) {
        SettingNotification currentType = EventBus.getDefault().getStickyEvent(SettingNotification.class);
        if (currentType == null) {
            currentType = new SettingNotification();
        }
        SettingNotification newType = currentType.addType(notification);
        EventBus.getDefault().postSticky(newType);
    }

    public static void cancelNotification(SettingNotificationType notification) {
        SettingNotification currentType = EventBus.getDefault().getStickyEvent(SettingNotification.class);
        if (currentType == null) {
            currentType = new SettingNotification();
        }
        SettingNotification newType = currentType.removeType(notification);
        EventBus.getDefault().postSticky(newType);
    }

    /**
     * A notification type, to be posted
     */
    @SuppressLint("ResourceAsColor")
    public enum SettingNotificationType {
        // higher priority notifications should go at the bottom of the list, as those colors will be returned first
        APK_UPDATE(R.color.md_green_500),
        CRASH_LOG(R.color.md_red_400);

        int color;

        SettingNotificationType(@ColorInt int color) {
            this.color = color;
        }
    }

    public static class SettingNotification {
        private final BitSet activeTypes = new BitSet(SettingNotificationType.values().length);

        public SettingNotification() {}

        public SettingNotification addType(SettingNotificationType type) {
            if (type == null) return this;
            activeTypes.set(type.ordinal());
            return this;
        }

        public SettingNotification removeType(SettingNotificationType type) {
            if (type == null) return this;
            activeTypes.clear(type.ordinal());
            return this;
        }

        public boolean contains(SettingNotification other) {
            if (other == null) return false;
            BitSet intermediate = (BitSet) activeTypes.clone();
            intermediate.and(other.activeTypes);
            return intermediate.cardinality() > 0;
        }

        public boolean hasActiveTypes() {
            return activeTypes.cardinality() > 0;
        }

        public int getColor() {
            for (int i = SettingNotificationType.values().length - 1; i >= 0; i--) {
                if (activeTypes.get(i)) {
                    return SettingNotificationType.values()[i].color;
                }
            }
            return android.R.color.transparent;
        }
    }
}

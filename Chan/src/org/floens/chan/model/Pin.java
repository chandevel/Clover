package org.floens.chan.model;

import org.floens.chan.watch.PinWatcher;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Pin {
    // Database stuff
    @DatabaseField(generatedId = true)
    private int id;

    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable = new Loadable("", -1);

    // ListView Stuff
    /** Header is used to display a static header in the drawer listview. */
    public Type type = Type.THREAD;
    public static enum Type {
        HEADER,
        THREAD
    };

    // PinnedService stuff
    public PinWatcher pinWatcher;

    @DatabaseField
    public int watchLastCount;

    @DatabaseField
    public int watchNewCount;

    public void updateWatch() {
        if (pinWatcher == null) {
            pinWatcher = new PinWatcher(this);
        }

        pinWatcher.update();
    }

    public int getNewPostCount() {
        if (pinWatcher != null) {
            return pinWatcher.getNewPostCount();
        } else {
            return 0;
        }
    }

    public void onViewed() {
        watchLastCount = watchNewCount;
    }

    public void destroy() {
        if (pinWatcher != null) {
            pinWatcher.destroy();
        }
    }
}




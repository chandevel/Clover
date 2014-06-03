/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.watch.PinWatcher;

@DatabaseTable
public class Pin {
    // Database stuff
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable = new Loadable("", -1);

    // ListView Stuff
    /**
     * Header is used to display a static header in the drawer listview.
     */
    public Type type = Type.THREAD;

    public static enum Type {
        HEADER, THREAD
    }

    // PinnedService stuff
    public PinWatcher pinWatcher;

    @DatabaseField
    public boolean watching = true;

    @DatabaseField
    public int watchLastCount = -1;

    @DatabaseField
    public int watchNewCount = -1;

    @DatabaseField
    public int quoteLastCount = 0;

    @DatabaseField
    public int quoteNewCount = 0;

    public boolean isError = false;

    public int getNewPostsCount() {
        if (watchLastCount < 0 || watchNewCount < 0) {
            return 0;
        } else {
            return Math.max(0, watchNewCount - watchLastCount);
        }
    }

    public int getNewQuoteCount() {
        return Math.max(0, quoteNewCount - quoteLastCount);
    }

    public Post getLastSeenPost() {
        return getPinWatcher().getLastSeenPost();
    }

    public void updateWatch() {
        getPinWatcher().update();
    }

    public void destroyWatcher() {
        if (pinWatcher != null) {
            pinWatcher.destroy();
            pinWatcher = null;
        }
    }

    public void toggleWatch() {
        watching = !watching;
        ChanApplication.getPinnedManager().onPinsChanged();
        if (watching) {
            updateWatch();
        }
    }

    public PinWatcher getPinWatcher() {
        if (pinWatcher == null) {
            pinWatcher = new PinWatcher(this);
        }

        return pinWatcher;
    }
}

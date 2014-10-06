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
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(canBeNull = false, foreign = true)
    public Loadable loadable = new Loadable("", -1);

    @DatabaseField
    public boolean watching = true;

    @DatabaseField
    public int watchLastCount = -1;

    @DatabaseField
    public int watchNewCount = -1;

    @DatabaseField
    public int quoteLastCount = -1;

    @DatabaseField
    public int quoteNewCount = -1;

    @DatabaseField
    public boolean isError = false;

    @DatabaseField
    public String thumbnailUrl = null;

    private PinWatcher pinWatcher;

    public int getNewPostCount() {
        if (watchLastCount < 0 || watchNewCount < 0) {
            return 0;
        } else {
            return Math.max(0, watchNewCount - watchLastCount);
        }
    }

    public int getNewQuoteCount() {
        if (quoteNewCount < 0 || quoteLastCount < 0) {
            return 0;
        } else {
            return Math.max(0, quoteNewCount - quoteLastCount);
        }
    }

    public PinWatcher getPinWatcher() {
        return pinWatcher;
    }

    public void onBottomPostViewed() {
        if (pinWatcher != null) {
            pinWatcher.onViewed();
        }
    }

    public void update() {
        if (pinWatcher != null && watching) {
            pinWatcher.update();
        }
    }

    public void createWatcher() {
        if (pinWatcher == null) {
            pinWatcher = new PinWatcher(this);
        }
    }

    public void destroyWatcher() {
        if (pinWatcher != null) {
            pinWatcher.destroy();
            pinWatcher = null;
        }
    }

    public void toggleWatch() {
        watching = !watching;
        ChanApplication.getWatchManager().onPinsChanged();
        ChanApplication.getWatchManager().invokeLoadNow();
    }
}

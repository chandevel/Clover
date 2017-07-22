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
package org.floens.chan.core.presenter;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import org.floens.chan.utils.AndroidUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.getAppContext;
import static org.floens.chan.utils.AndroidUtils.getRes;

public class SetupPresenter {
    private Callback callback;

    private List<AddedSite> sites = new ArrayList<>();

    public void create(Callback callback) {
        this.callback = callback;

        this.callback.setAddedSites(sites);

        this.callback.setNextAllowed(!sites.isEmpty(), false);
    }

    public boolean mayExit() {
        return false;
    }

    public void onUrlSubmitClicked(String url) {
        callback.goToUrlSubmittedState();

        AndroidUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                siteAdded(getTestSite());
            }
        }, 500);
//        callback.showUrlHint("foo bar baz");
    }

    public void onNextClicked() {
        if (!sites.isEmpty()) {
            callback.moveToSavedBoards();
        }
    }

    private void siteAdded(AddedSite site) {
        sites.add(site);
        callback.setAddedSites(sites);
        callback.runSiteAddedAnimation(site);

        callback.setNextAllowed(!sites.isEmpty(), true);
    }

    private int counter;

    private AddedSite getTestSite() {
        AddedSite site = new AddedSite();
        site.id = counter++;
        site.title = "4chan.org";

        Bitmap bitmap;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inScaled = false;
            bitmap = BitmapFactory.decodeStream(getAppContext().getAssets().open("icons/4chan.png"), null, opts);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        BitmapDrawable drawable = new BitmapDrawable(getRes(), bitmap);
        drawable = (BitmapDrawable) drawable.mutate();
        drawable.getPaint().setFilterBitmap(false);
        site.drawable = drawable;
        return site;
    }

    public interface Callback {
        void goToUrlSubmittedState();

        void runSiteAddedAnimation(AddedSite site);

        void setAddedSites(List<AddedSite> sites);

        void setNextAllowed(boolean nextAllowed, boolean animate);

        void showUrlHint(String text);

        void moveToSavedBoards();
    }

    public static class AddedSite {
        public int id;
        public String title;
        public Drawable drawable;
    }
}

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
package org.floens.chan.ui.activity;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.MenuItem;

import org.floens.chan.core.model.Loadable;
import org.floens.chan.ui.fragment.ReplyFragment;
import org.floens.chan.utils.Logger;

public class ReplyActivity extends Activity {
    private static final String TAG = "ReplyActivity";

    private static Loadable loadable;

    public static void setLoadable(Loadable l) {
        loadable = l;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (loadable != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, ReplyFragment.newInstance(loadable));
            ft.commitAllowingStateLoss();

            loadable = null;
        } else {
            Logger.e(TAG, "ThreadFragment was null, exiting!");
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

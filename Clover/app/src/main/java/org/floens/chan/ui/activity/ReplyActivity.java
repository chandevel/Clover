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

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.view.MenuItem;

import org.floens.chan.R;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.ui.ThemeActivity;
import org.floens.chan.ui.fragment.ReplyFragment;
import org.floens.chan.utils.Logger;

public class ReplyActivity extends ThemeActivity {
    private static final String TAG = "ReplyActivity";

    private static Loadable staticLoadable;

    public static void setLoadable(Loadable l) {
        staticLoadable = l;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Loadable loadable = staticLoadable;
        staticLoadable = null;

        if (loadable != null && savedInstanceState == null) {
            setTheme();
            setContentView(R.layout.toolbar_activity);
            setToolbar();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.content, ReplyFragment.newInstance(loadable, false), "reply");
            ft.commitAllowingStateLoss();
        } else if (savedInstanceState == null) {
            Logger.e(TAG, "Loadable was null, exiting!");
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        Fragment f = getFragmentManager().findFragmentByTag("reply");
        if (f != null && ((ReplyFragment)f).onBackPressed()) {
            super.onBackPressed();
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

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
package org.floens.chan.ui.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.Arrays;

public class ViewThreadController extends ThreadController implements ThreadLayout.ThreadLayoutCallback, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final int POST_ID = 1;
    private static final int PIN_ID = 2;
    private static final int REFRESH_ID = 101;
    private static final int SEARCH_ID = 102;
    private static final int SHARE_ID = 103;

    private ToolbarMenuItem pinItem;
    private Loadable loadable;

    public ViewThreadController(Context context) {
        super(context);
    }

    public void setLoadable(Loadable loadable) {
        this.loadable = loadable;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view.setBackgroundColor(0xffffffff);

        threadLayout.getPresenter().bindLoadable(loadable);
        threadLayout.getPresenter().requestData();

        navigationItem.hasDrawer = true;
        navigationItem.title = loadable.title;
        navigationItem.menu = new ToolbarMenu(context);

        navigationItem.menu.addItem(new ToolbarMenuItem(context, this, POST_ID, R.drawable.ic_action_write));
        pinItem = navigationItem.menu.addItem(new ToolbarMenuItem(context, this, PIN_ID, R.drawable.ic_bookmark));
        navigationItem.createOverflow(context, this, Arrays.asList(
                new FloatingMenuItem(REFRESH_ID, context.getString(R.string.action_reload)),
                new FloatingMenuItem(SEARCH_ID, context.getString(R.string.action_search)),
                new FloatingMenuItem(SHARE_ID, context.getString(R.string.action_share))
        ));

        setPinIconState(threadLayout.getPresenter().isPinned());
    }

    @Override
    public void openThread(Loadable threadLoadable) {
        // TODO implement, scroll to post and fix title
        new AlertDialog.Builder(context)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
//                        threadManagerListener.onOpenThread(thread, link.postId);
                    }
                })
                .setTitle(R.string.open_thread_confirmation)
                .setMessage("/" + threadLoadable.board + "/" + threadLoadable.no)
                .show();
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch (item.getId()) {
            case PIN_ID:
                setPinIconState(threadLayout.getPresenter().pin());
                break;
            case POST_ID:
                // TODO
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        switch ((Integer) item.getId()) {
            case REFRESH_ID:
                threadLayout.getPresenter().requestData();
                break;
            case SEARCH_ID:
                // TODO
                break;
            case SHARE_ID:
                Loadable loadable = threadLayout.getPresenter().getLoadable();
                String link = ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no);
                AndroidUtils.shareLink(link);
                break;
        }
    }

    private void setPinIconState(boolean pinned) {
        pinItem.setImage(pinned ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark);
    }
}

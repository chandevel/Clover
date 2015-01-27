package org.floens.chan.ui.controller;

import android.content.Context;

import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.toolbar.ToolbarMenuSubItem;
import org.floens.chan.ui.toolbar.ToolbarMenuSubMenu;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

public class BrowseController extends Controller implements ToolbarMenuItem.ToolbarMenuItemCallback, ThreadLayout.ThreadLayoutCallback {
    private static final int REFRESH_ID = 1;
    private static final int POST_ID = 2;
    private static final int SEARCH_ID = 101;
    private static final int SHARE_ID = 102;
    private static final int SETTINGS_ID = 103;

    private ThreadLayout threadLayout;

    public BrowseController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = "Hello world";
        ToolbarMenu menu = new ToolbarMenu(context);
        navigationItem.menu = menu;
        navigationItem.hasBack = false;

        menu.addItem(new ToolbarMenuItem(context, this, REFRESH_ID, R.drawable.ic_action_refresh));
        menu.addItem(new ToolbarMenuItem(context, this, POST_ID, R.drawable.ic_action_write));

        ToolbarMenuItem overflow = menu.createOverflow(this);

        List<ToolbarMenuSubItem> items = new ArrayList<>();
        items.add(new ToolbarMenuSubItem(SEARCH_ID, context.getString(R.string.action_search)));
        items.add(new ToolbarMenuSubItem(SHARE_ID, context.getString(R.string.action_share)));
        items.add(new ToolbarMenuSubItem(SETTINGS_ID, context.getString(R.string.action_settings)));

        overflow.setSubMenu(new ToolbarMenuSubMenu(context, overflow.getView(), items));

        threadLayout = new ThreadLayout(context);
        threadLayout.setCallback(this);

        view = threadLayout;

        Loadable loadable = new Loadable("g");
        loadable.mode = Loadable.Mode.CATALOG;
        loadable.generateTitle();
        navigationItem.title = loadable.title;

        threadLayout.getPresenter().bindLoadable(loadable);
        threadLayout.getPresenter().requestData();
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch (item.getId()) {
            case REFRESH_ID:
                threadLayout.getPresenter().requestData();
                break;
            case POST_ID:
                // TODO
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, ToolbarMenuSubItem item) {
        switch (item.getId()) {
            case SEARCH_ID:
                // TODO
                break;
            case SHARE_ID:
                String link = ChanUrls.getCatalogUrlDesktop(threadLayout.getPresenter().getLoadable().board);
                AndroidUtils.shareLink(link);
                break;
            case SETTINGS_ID:
                SettingsController settingsController = new SettingsController(context);
                navigationController.pushController(settingsController);
                break;
        }
    }

    @Override
    public void openThread(Loadable threadLoadable) {
        ViewThreadController viewThreadController = new ViewThreadController(context);
        viewThreadController.setLoadable(threadLoadable);
        navigationController.pushController(viewThreadController);
    }
}

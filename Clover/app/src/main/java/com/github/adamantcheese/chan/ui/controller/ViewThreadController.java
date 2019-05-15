/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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
package com.github.adamantcheese.chan.ui.controller;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v7.app.AlertDialog;
import android.view.View;

import com.adamantcheese.github.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.controller.NavigationController;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.manager.WatchManager.PinMessages;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.HintPopup;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenu;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;

public class ViewThreadController extends ThreadController implements ThreadLayout.ThreadLayoutCallback {
    private static final int PIN_ID = 1;

    @Inject
    WatchManager watchManager;

    private boolean pinItemPinned = false;
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
        inject(this);

        threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST);

        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));

        navigation.hasDrawer = true;

        NavigationItem.MenuOverflowBuilder menuOverflowBuilder = navigation.buildMenu()
                .withItem(R.drawable.ic_image_white_24dp, this::albumClicked)
                .withItem(PIN_ID, R.drawable.ic_bookmark_outline_white_24dp, this::pinClicked)
                .withOverflow();

        if (!ChanSettings.enableReplyFab.get()) {
            menuOverflowBuilder.withSubItem(R.string.action_reply, this::replyClicked);
        }

        menuOverflowBuilder
                .withSubItem(R.string.action_search, this::searchClicked)
                .withSubItem(R.string.action_reload, this::reloadClicked)
                .withSubItem(R.string.action_open_browser, this::openBrowserClicked)
                .withSubItem(R.string.action_share, this::shareClicked)
                .withSubItem(R.string.action_scroll_to_top, this::upClicked)
                .withSubItem(R.string.action_scroll_to_bottom, this::downClicked)
                .build()
                .build();

        loadThread(loadable);
    }

    private void albumClicked(ToolbarMenuItem item) {
        threadLayout.getPresenter().showAlbum();
    }

    private void pinClicked(ToolbarMenuItem item) {
        threadLayout.getPresenter().pin();
        setPinIconState(true);
        updateDrawerHighlighting(loadable);
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    private void replyClicked(ToolbarMenuSubItem item) {
        threadLayout.openReply(true);
    }

    private void reloadClicked(ToolbarMenuSubItem item) {
        threadLayout.getPresenter().requestData();
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        Loadable loadable = threadLayout.getPresenter().getLoadable();
        String link = loadable.site.resolvable().desktopUrl(loadable, null);
        AndroidUtils.openLinkInBrowser((Activity) context, link);
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        Loadable loadable = threadLayout.getPresenter().getLoadable();
        String link = loadable.site.resolvable().desktopUrl(loadable, null);
        AndroidUtils.shareLink(link);
    }

    private void upClicked(ToolbarMenuSubItem item) {
        threadLayout.getPresenter().scrollTo(0, false);
    }

    private void downClicked(ToolbarMenuSubItem item) {
        threadLayout.getPresenter().scrollTo(-1, false);
    }

    @Override
    public void onShow() {
        super.onShow();

        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter != null) {
            setPinIconState(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateDrawerHighlighting(null);
        updateLeftPaneHighlighting(null);
    }

    @Override
    public void openPin(Pin pin) {
        loadThread(pin.loadable);
    }

    public void onEvent(PinMessages.PinAddedMessage message) {
        setPinIconState(true);
    }

    public void onEvent(PinMessages.PinRemovedMessage message) {
        setPinIconState(true);
    }

    public void onEvent(PinMessages.PinChangedMessage message) {
        setPinIconState(false);
        // Update title
        if (message.pin.loadable == loadable) {
            onShowPosts();
        }
    }

    public void onEvent(PinMessages.PinsChangedMessage message) {
        setPinIconState(true);
    }

    @Override
    public void showThread(final Loadable threadLoadable) {
        new AlertDialog.Builder(context)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> loadThread(threadLoadable))
                .setTitle(R.string.open_thread_confirmation)
                .setMessage("/" + threadLoadable.boardCode + "/" + threadLoadable.no)
                .show();
    }

    @Override
    public void showBoard(final Loadable catalogLoadable) {
        if (doubleNavigationController != null && doubleNavigationController.getLeftController() instanceof BrowseController) {
            ((BrowseController) doubleNavigationController.getLeftController()).setBoard(catalogLoadable.board);
            doubleNavigationController.switchToController(true);
        } else if (doubleNavigationController != null && doubleNavigationController.getLeftController() instanceof StyledToolbarNavigationController) {
            ((BrowseController) doubleNavigationController.getLeftController().childControllers.get(0)).setBoard(catalogLoadable.board);
        } else {
            BrowseController browseController = null;
            for(Controller c : navigationController.childControllers) {
                if (c instanceof  BrowseController) {
                    browseController = (BrowseController) c;
                    break;
                }
            }
            if(browseController != null) {
                browseController.setBoard(catalogLoadable.board);
            }
            navigationController.popController();
        }
    }

    public void loadThread(Loadable loadable) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (!loadable.equals(presenter.getLoadable())) {
            presenter.bindLoadable(loadable);
            this.loadable = presenter.getLoadable();
            navigation.title = loadable.title;
            ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
            setPinIconState(false);
            updateDrawerHighlighting(loadable);
            updateLeftPaneHighlighting(loadable);
            presenter.requestInitialData();

            showHints();
        }
    }

    private void showHints() {
        int counter = ChanSettings.threadOpenCounter.increase();
        if (counter == 2) {
            view.postDelayed(() -> {
                View view = navigation.findItem(ToolbarMenu.OVERFLOW_ID).getView();
                if (view != null) {
                    HintPopup.show(context, view, context.getString(R.string.thread_up_down_hint), -dp(1), 0);
                }
            }, 600);
        } else if (counter == 3) {
            view.postDelayed(() -> {
                View view = navigation.findItem(PIN_ID).getView();
                if (view != null) {
                    HintPopup.show(context, view, context.getString(R.string.thread_pin_hint), -dp(1), 0);
                }
            }, 600);
        }
    }

    @Override
    public void onShowPosts() {
        super.onShowPosts();

        navigation.title = loadable.title;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
    }

    private void updateDrawerHighlighting(Loadable loadable) {
        Pin pin = loadable == null ? null : watchManager.findPinByLoadable(loadable);

        if (navigationController.parentController instanceof DrawerController) {
            ((DrawerController) navigationController.parentController).setPinHighlighted(pin);
        } else if (doubleNavigationController != null) {
            Controller doubleNav = (Controller) doubleNavigationController;
            if (doubleNav.parentController instanceof DrawerController) {
                ((DrawerController) doubleNav.parentController).setPinHighlighted(pin);
            }
        }
    }

    private void updateLeftPaneHighlighting(Loadable loadable) {
        if (doubleNavigationController != null) {
            ThreadController threadController = null;
            Controller leftController = doubleNavigationController.getLeftController();
            if (leftController instanceof ThreadController) {
                threadController = (ThreadController) leftController;
            } else if (leftController instanceof NavigationController) {
                NavigationController leftNavigationController = (NavigationController) leftController;
                for (Controller controller : leftNavigationController.childControllers) {
                    if (controller instanceof ThreadController) {
                        threadController = (ThreadController) controller;
                        break;
                    }
                }
            }
            if (threadController != null) {
                threadController.selectPost(loadable != null ? loadable.no : -1);
            }
        }
    }

    private void setPinIconState(boolean animated) {
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (presenter != null) {
            setPinIconStateDrawable(presenter.isPinned(), animated);
        }
    }

    private void setPinIconStateDrawable(boolean pinned, boolean animated) {
        if (pinned == pinItemPinned) {
            return;
        }
        pinItemPinned = pinned;

        Drawable outline = context.getResources().getDrawable(
                R.drawable.ic_bookmark_outline_white_24dp);
        Drawable white = context.getResources().getDrawable(
                R.drawable.ic_bookmark_white_24dp);

        Drawable drawable = pinned ? white : outline;

        navigation.findItem(PIN_ID).setImage(drawable, animated);
    }
}

/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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

import android.content.Context;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.view.KeyEvent;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.ui.layout.ThreadLayout;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.Logger;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isTablet;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public abstract class ThreadController
        extends Controller
        implements ThreadLayout.ThreadLayoutCallback, ImageViewerController.ImageViewerCallback,
                   SwipeRefreshLayout.OnRefreshListener, ToolbarNavigationController.ToolbarSearchCallback,
                   NfcAdapter.CreateNdefMessageCallback, ThreadSlideController.SlideChangeListener {
    private static final String TAG = "ThreadController";

    protected ThreadLayout threadLayout;
    private SwipeRefreshLayout swipeRefreshLayout;

    public ThreadController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);

        navigation.handlesToolbarInset = true;

        threadLayout = (ThreadLayout) inflate(context, R.layout.layout_thread, null);
        threadLayout.create(this);

        swipeRefreshLayout = new SwipeRefreshLayout(context) {
            @Override
            public boolean canChildScrollUp() {
                return threadLayout.canChildScrollUp();
            }
        };
        swipeRefreshLayout.addView(threadLayout);

        swipeRefreshLayout.setOnRefreshListener(this);

        if (navigation.handlesToolbarInset) {
            int toolbarHeight = getToolbar().getToolbarHeight();
            swipeRefreshLayout.setProgressViewOffset(false, toolbarHeight - dp(40), toolbarHeight + dp(64 - 40));
        }

        view = swipeRefreshLayout;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        threadLayout.destroy();

        EventBus.getDefault().unregister(this);
    }

    public void showSitesNotSetup() {
        threadLayout.getPresenter().showNoContent();
    }

    public abstract void openPin(Pin pin);

    /*
     * Used to save instance state
     */
    public Loadable getLoadable() {
        return threadLayout.getPresenter().getLoadable();
    }

    public void selectPost(int post) {
        threadLayout.getPresenter().selectPost(post);
    }

    @Override
    public boolean onBack() {
        return threadLayout.onBack();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return threadLayout.sendKeyEvent(event) || super.dispatchKeyEvent(event);
    }

    @Subscribe
    public void onEvent(Chan.ForegroundChangedMessage message) {
        threadLayout.getPresenter().onForegroundChanged(message.inForeground);
    }

    @Subscribe
    public void onEvent(RefreshUIMessage message) {
        threadLayout.getPresenter().requestData();
    }

    @Override
    public void onRefresh() {
        threadLayout.refreshFromSwipe();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        if (threadLayout.getPresenter().getChanThread() == null) {
            showToast(R.string.cannot_send_thread_via_nfc_already_deleted);
            return null;
        }

        Loadable loadable = getLoadable();
        String url = null;
        NdefMessage message = null;

        if (loadable != null) {
            url = loadable.desktopUrl();
        }

        if (url != null) {
            try {
                Logger.d(TAG, "Pushing url " + url + " to android beam");
                NdefRecord record = NdefRecord.createUri(url);
                message = new NdefMessage(new NdefRecord[]{record});
            } catch (IllegalArgumentException e) {
                Logger.e(TAG, "NdefMessage create error", e);
            }
        }

        return message;
    }

    @Override
    public void openReportController(final Post post) {
        navigationController.pushController(new ReportController(context, post));
    }

    public void selectPostImage(PostImage postImage) {
        threadLayout.getPresenter().selectPostImage(postImage);
    }

    @Override
    public void showImages(List<PostImage> images, int index, Loadable loadable, final ThumbnailView thumbnail) {
        // Just ignore the showImages request when the image is not loaded
        if (thumbnail.getBitmap() != null && !isAlreadyPresenting()) {
            final ImageViewerNavigationController imagerViewer = new ImageViewerNavigationController(context);
            presentController(imagerViewer, false);
            imagerViewer.showImages(images, index, loadable, this);
        }
    }

    @Override
    public ThumbnailView getPreviewImageTransitionView(PostImage postImage) {
        return threadLayout.getThumbnail(postImage);
    }

    @Override
    public void scrollToImage(PostImage postImage) {
        threadLayout.getPresenter().scrollToImage(postImage, true);
    }

    @Override
    public void showAlbum(List<PostImage> images, int index) {
        if (threadLayout.getPresenter().getChanThread() != null) {
            AlbumViewController albumViewController = new AlbumViewController(context);
            albumViewController.setImages(getLoadable(), images, index, navigation.title);

            if (doubleNavigationController != null) {
                doubleNavigationController.pushController(albumViewController);
            } else {
                navigationController.pushController(albumViewController);
            }
        }
    }

    @Override
    public void onShowPosts() {
    }

    @Override
    public void hideSwipeRefreshLayout() {
        if (swipeRefreshLayout == null) {
            return;
        }

        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public Toolbar getToolbar() {
        if (navigationController instanceof ToolbarNavigationController) {
            return navigationController.getToolbar();
        } else {
            return null;
        }
    }

    @Override
    public boolean shouldToolbarCollapse() {
        return !isTablet() && !ChanSettings.neverHideToolbar.get();
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        threadLayout.getPresenter().onSearchVisibilityChanged(visible);
    }

    @Override
    public void onSearchEntered(String entered) {
        threadLayout.getPresenter().onSearchEntered(entered);
    }

    @Override
    public void openFilterForTripcode(String tripcode) {
        FiltersController filtersController = new FiltersController(context);
        if (doubleNavigationController != null) {
            doubleNavigationController.pushController(filtersController);
        } else {
            navigationController.pushController(filtersController);
        }
        // TODO cleanup
        Filter filter = new Filter();
        filter.type = FilterType.TRIPCODE.flag;
        filter.pattern = String.format("/%s/", tripcode);
        filtersController.showFilterDialog(filter);
    }

    @Override
    public void onSlideChanged() {
        threadLayout.gainedFocus();
    }

    @Override
    public boolean threadBackPressed() {
        return false;
    }
}

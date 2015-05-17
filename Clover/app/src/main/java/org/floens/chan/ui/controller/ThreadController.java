package org.floens.chan.ui.controller;

import android.content.Context;

import org.floens.chan.ChanApplication;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.List;

import de.greenrobot.event.EventBus;

public abstract class ThreadController extends Controller implements ThreadLayout.ThreadLayoutCallback, ImageViewerController.PreviewCallback, RootNavigationController.DrawerCallbacks {
    protected ThreadLayout threadLayout;

    public ThreadController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EventBus.getDefault().register(this);

        threadLayout = new ThreadLayout(context);
        threadLayout.setCallback(this);
        view = threadLayout;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        threadLayout.getPresenter().unbindLoadable();

        EventBus.getDefault().unregister(this);
    }

    @Override
    public boolean onBack() {
        return threadLayout.onBack();
    }

    public void onEvent(ChanApplication.ForegroundChangedMessage message) {
        threadLayout.getPresenter().onForegroundChanged(message.inForeground);
    }

    public void openPost(boolean open) {
        threadLayout.openPost(open);
    }

    public void presentRepliesController(Controller controller) {
        presentController(controller);
    }

    @Override
    public void showImages(List<PostImage> images, int index, Loadable loadable, final ThumbnailView thumbnail) {
        // Just ignore the showImages request when the image is not loaded
        if (thumbnail.getBitmap() != null) {
            final ImageViewerNavigationController imageViewerNavigationController = new ImageViewerNavigationController(context);
            presentController(imageViewerNavigationController, false);
            imageViewerNavigationController.showImages(images, index, loadable, this);
        }
    }

    @Override
    public ThumbnailView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage) {
        return threadLayout.getThumbnail(postImage);
    }

    public void onPreviewCreate(ImageViewerController imageViewerController) {
//        presentingImageView.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onPreviewDestroy(ImageViewerController imageViewerController) {
//        presentingImageView.setVisibility(View.VISIBLE);
//        presentingImageView = null;
    }

    public void scrollToImage(PostImage postImage) {
        if (!threadLayout.postRepliesOpen()) {
            threadLayout.getPresenter().scrollToImage(postImage, true);
        }
    }

    @Override
    public void onShowPosts() {
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        threadLayout.getPresenter().onSearchVisibilityChanged(visible);
    }

    @Override
    public void onSearchEntered(String entered) {
        threadLayout.getPresenter().onSearchEntered(entered);
    }
}

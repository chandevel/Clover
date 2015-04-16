package org.floens.chan.ui.controller;

import android.content.Context;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.List;

public abstract class ThreadController extends Controller implements ThreadLayout.ThreadLayoutCallback, ImageViewerController.PreviewCallback {
    protected ThreadLayout threadLayout;

    public ThreadController(Context context) {
        super(context);

        threadLayout = new ThreadLayout(context);
        threadLayout.setCallback(this);
        view = threadLayout;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        threadLayout.getPresenter().unbindLoadable();
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
            threadLayout.getPresenter().scrollTo(postImage);
        }
    }

    @Override
    public void onShowPosts() {
    }
}

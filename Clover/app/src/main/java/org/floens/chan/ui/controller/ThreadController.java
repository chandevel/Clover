package org.floens.chan.ui.controller;

import android.content.Context;
import android.widget.ImageView;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.layout.ThreadLayout;

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
    public void showImages(List<PostImage> images, int index, Loadable loadable, final ImageView thumbnail) {
        // Just ignore the showImages request when the image is not loaded
        if (thumbnail.getDrawable() != null && thumbnail.getDrawable().getIntrinsicWidth() > 0 && thumbnail.getDrawable().getIntrinsicHeight() > 0) {
            final ImageViewerNavigationController imageViewerNavigationController = new ImageViewerNavigationController(context);
            presentController(imageViewerNavigationController, false);
            imageViewerNavigationController.showImages(images, index, loadable, this);
        }
    }

    @Override
    public ImageView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage) {
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

    public void scrollTo(PostImage postImage) {
        threadLayout.getPresenter().scrollTo(postImage);
    }
}

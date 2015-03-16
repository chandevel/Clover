package org.floens.chan.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.layout.ThreadLayout;

import java.util.List;

public abstract class ThreadController extends Controller implements ThreadLayout.ThreadLayoutCallback, ImageViewerController.Callback {
    protected ThreadLayout threadLayout;
    private ImageView presentingImageView;

    public ThreadController(Context context) {
        super(context);

        threadLayout = new ThreadLayout(context);
        threadLayout.setCallback(this);
        view = threadLayout;
    }

    @Override
    public void showImages(List<PostImage> images, int index, final ImageView thumbnail) {
        presentingImageView = thumbnail;
        presentingImageView.setVisibility(View.INVISIBLE);

        final ImageViewerNavigationController imageViewerNavigationController = new ImageViewerNavigationController(context);
        presentController(imageViewerNavigationController, false);
        imageViewerNavigationController.setImage(this, thumbnail);
    }

    @Override
    public ImageView getPreviewImageStartView(ImageViewerController imageViewerController) {
        return presentingImageView;
    }

    @Override
    public void onPreviewDestroy(ImageViewerController imageViewerController) {
        presentingImageView.setVisibility(View.VISIBLE);
        presentingImageView = null;
    }
}

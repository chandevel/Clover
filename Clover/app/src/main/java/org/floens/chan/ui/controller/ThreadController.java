package org.floens.chan.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;

public abstract class ThreadController extends Controller implements ThreadLayout.ThreadLayoutCallback, ImageViewController.Callback {
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

        final ImageViewController imageViewController = new ImageViewController(context);
        presentController(imageViewController, false);
        AndroidUtils.waitForMeasure(imageViewController.view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                imageViewController.setImage(ThreadController.this, thumbnail);
                return true;
            }
        });
    }

    @Override
    public ImageView getImageView(ImageViewController imageViewController) {
        return presentingImageView;
    }

    @Override
    public void onImageViewLayoutDestroy(ImageViewController imageViewController) {
        presentingImageView.setVisibility(View.VISIBLE);
        presentingImageView = null;
    }
}

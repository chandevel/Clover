package org.floens.chan.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import org.floens.chan.R;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;

public class ImageViewerNavigationController extends NavigationController {
    private ImageViewerController imageViewerController;

    public ImageViewerNavigationController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_navigation_image_viewer);
        toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        container = (FrameLayout) view.findViewById(R.id.container);

        toolbar.setCallback(this);

        imageViewerController = new ImageViewerController(context, toolbar);
        pushController(imageViewerController, false);
    }

    public void showImages(final List<PostImage> images, final int index, final ImageViewerController.Callback callback) {
        AndroidUtils.waitForMeasure(imageViewerController.view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                imageViewerController.setCallback(callback);
                imageViewerController.getPresenter().showImages(images, index);
                return true;
            }
        });
    }
}

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

import android.content.Context;
import android.widget.FrameLayout;

import org.floens.chan.R;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.toolbar.Toolbar;

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

    public void showImages(final List<PostImage> images, final int index, final Loadable loadable, final ImageViewerController.PreviewCallback previewCallback) {
        imageViewerController.setPreviewCallback(previewCallback);
        imageViewerController.getPresenter().showImages(images, index, loadable);
    }
}

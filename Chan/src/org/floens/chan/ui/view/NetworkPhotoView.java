/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
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
package org.floens.chan.ui.view;

import org.floens.chan.R;
import org.floens.chan.ui.fragment.ImageViewFragment;

import uk.co.senab.photoview.PhotoViewAttacher;
import uk.co.senab.photoview.PhotoViewAttacher.OnViewTapListener;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import com.android.volley.VolleyError;

/**
 * Extends NetworkImageView. Attaches a PhotoViewAttacher when setBitmap is
 * called. Sets the progressBar to false when a bitmap gets set.
 */
public class NetworkPhotoView extends CustomNetworkImageView {
    private PhotoViewAttacher attacher;
    private OnLongClickListener longClickListener;
    private OnViewTapListener viewTapListener;
    private ImageViewFragment fragment;

    public NetworkPhotoView(Context context) {
        super(context);
    }

    public void setImageViewFragment(ImageViewFragment fragment) {
        this.fragment = fragment;
    }

    public void setOnLongClickListenerToAttacher(OnLongClickListener listener) {
        longClickListener = listener;
    }

    public void setOnViewTapListenerToAttacher(OnViewTapListener listener) {
        viewTapListener = listener;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);

        attacher = new PhotoViewAttacher(this);
        attacher.setMaximumScale(getMaxScale());
        attacher.setOnLongClickListener(longClickListener);
        attacher.setOnViewTapListener(viewTapListener);

        fragment.showProgressBar(false);
    }

    @Override
    public void onErrorResponse(VolleyError error) {
        super.onErrorResponse(error);

        // TODO: out of memory. We need a new image viewer for *large* images. Maybe copy the one from the gallery.
        System.gc();
        Toast.makeText(getContext(), R.string.image_preview_failed, Toast.LENGTH_LONG).show();

        fragment.showProgressBar(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (attacher != null) {
            attacher.cleanup();
        }
    }
}

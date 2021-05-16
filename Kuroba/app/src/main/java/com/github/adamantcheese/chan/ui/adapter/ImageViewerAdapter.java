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
package com.github.adamantcheese.chan.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.ui.view.ViewPagerAdapter;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.skydoves.balloon.Balloon;

import java.util.ArrayList;
import java.util.List;

public class ImageViewerAdapter
        extends ViewPagerAdapter {
    private final List<PostImage> images;
    private final MultiImageView.Callback multiImageViewCallback;

    private final List<MultiImageView> loadedViews = new ArrayList<>(3);
    private final List<ModeChange> pendingModeChanges = new ArrayList<>();

    public ImageViewerAdapter(
            List<PostImage> images, MultiImageView.Callback multiImageViewCallback
    ) {
        this.images = images;
        this.multiImageViewCallback = multiImageViewCallback;
    }

    @Override
    public View getView(int position, ViewGroup parent) {
        PostImage postImage = images.get(position);
        MultiImageView view = new MultiImageView(parent.getContext());
        view.bindPostImage(postImage, multiImageViewCallback, images.get(0) == postImage); // hacky but good enough

        loadedViews.add(view);

        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);

        //noinspection SuspiciousMethodCalls
        loadedViews.remove(object);
    }

    @Override
    public int getCount() {
        return images.size();
    }

    public MultiImageView find(PostImage postImage) {
        for (MultiImageView view : loadedViews) {
            if (view.getPostImage() == postImage) {
                return view;
            }
        }
        return null;
    }

    @Override
    public void finishUpdate(@NonNull ViewGroup container) {
        for (ModeChange change : pendingModeChanges) {
            MultiImageView view = find(change.postImage);
            if (view == null || view.getWindowToken() == null) {
                Logger.w(this, "finishUpdate setMode view still not found");
            } else {
                setModeInternal(change.postImage, change.mode, change.center);
            }
        }
        pendingModeChanges.clear();
    }

    public void setMode(final PostImage postImage, MultiImageView.Mode mode, boolean center) {
        MultiImageView view = find(postImage);
        if (view == null || view.getWindowToken() == null) {
            pendingModeChanges.add(new ModeChange(mode, postImage, center));
        } else {
            setModeInternal(postImage, mode, center);
        }
    }

    private void setModeInternal(PostImage image, MultiImageView.Mode mode, boolean center) {
        MultiImageView view = find(image);
        view.setMode(mode, center);

        Balloon.Builder hintBuilder = AndroidUtils.getBaseToolTip(view.getContext())
                .setPreferenceName(mode.name() + "HINT")
                .setIsVisibleArrow(false);
        switch (mode) {
            case VIDEO:
                hintBuilder.setText("Single tap for controls" + "\nDouble tap to pause/play").build().show(view);
                break;
            case BIGIMAGE:
                hintBuilder.setText("Two finger tap to rotate" + "\nSecond finger left of first rotates clockwise"
                        + "\nSecond finger right of first rotates counter-clockwise").build().show(view);
                break;
            case GIFIMAGE:
                hintBuilder.setText("Double tap to pause/play").build().show(view);
                break;
            case OTHER:
            case LOWRES:
            case WEBVIEW:
            case UNLOADED:
            default:
                // no hints for these modes
                break;
        }
    }

    public void setVolume(PostImage postImage, boolean muted) {
        // It must be loaded, or the user is not able to click the menu item.
        MultiImageView view = find(postImage);
        if (view != null) {
            view.setVolume(muted);
        }
    }

    public MultiImageView.Mode getMode(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view == null) {
            Logger.w(this, "getMode view not found");
            return null;
        } else {
            return view.getMode();
        }
    }

    public void toggleTransparency(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view != null) {
            view.toggleOpacity();
        }
    }

    public void onImageSaved(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view != null) {
            view.setImageAlreadySaved();
        }
    }

    private static class ModeChange {
        public MultiImageView.Mode mode;
        public PostImage postImage;
        public boolean center;

        private ModeChange(MultiImageView.Mode mode, PostImage postImage, boolean center) {
            this.mode = mode;
            this.postImage = postImage;
            this.center = center;
        }
    }
}

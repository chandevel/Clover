package org.floens.chan.ui.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.view.MultiImageView;
import org.floens.chan.ui.view.ViewPagerAdapter;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class ImageViewerAdapter extends ViewPagerAdapter {
    private static final String TAG = "ImageViewerAdapter";

    private final Context context;
    private final List<PostImage> images;
    private final MultiImageView.Callback multiImageViewCallback;

    private List<MultiImageView> loadedViews = new ArrayList<>(3);
    private List<ModeChange> pendingModeChanges = new ArrayList<>();

    public ImageViewerAdapter(Context context, List<PostImage> images, MultiImageView.Callback multiImageViewCallback) {
        this.context = context;
        this.images = images;
        this.multiImageViewCallback = multiImageViewCallback;
    }

    @Override
    public View getView(int position) {
        PostImage postImage = images.get(position);
        MultiImageView view = new MultiImageView(context);
        view.bindPostImage(postImage, multiImageViewCallback);

        loadedViews.add(view);

        Logger.test("getView: " + postImage.imageUrl + " " + postImage.type.toString());

        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);

        PostImage postImage = ((MultiImageView)object).getPostImage();
        Logger.test("destroyView: " + postImage.imageUrl + " " + postImage.type.toString());

        //noinspection SuspiciousMethodCalls
        if (!loadedViews.remove((View) object)) {
            Logger.test("Nope");
        }
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public void finishUpdate(ViewGroup container) {
        for (ModeChange change : pendingModeChanges) {
            MultiImageView view = find(change.postImage);
            if (view == null) {
                Logger.w(TAG, "finishUpdate setMode view still not found");
            } else {
                view.setMode(change.mode);
            }
        }
        pendingModeChanges.clear();
    }

    public void setMode(final PostImage postImage, MultiImageView.Mode mode) {
        MultiImageView view = find(postImage);
        if (view == null) {
            Logger.w(TAG, "setMode view not found, scheduling it");
            pendingModeChanges.add(new ModeChange(mode, postImage));
        } else {
            view.setMode(mode);
        }
    }

    public MultiImageView.Mode getMode(PostImage postImage) {
        MultiImageView view = find(postImage);
        if (view == null) {
            Logger.w(TAG, "getMode view not found");
            return null;
        } else {
            return view.getMode();
        }
    }

    public MultiImageView find(PostImage postImage) {
        for (MultiImageView view : loadedViews) {
            if (view.getPostImage() == postImage) {
                return view;
            }
        }
        return null;
    }

    private static class ModeChange {
        public MultiImageView.Mode mode;
        public PostImage postImage;

        private ModeChange(MultiImageView.Mode mode, PostImage postImage) {
            this.mode = mode;
            this.postImage = postImage;
        }
    }
}

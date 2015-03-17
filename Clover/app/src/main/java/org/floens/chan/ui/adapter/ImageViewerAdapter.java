package org.floens.chan.ui.adapter;

import android.content.Context;
import android.view.View;

import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.view.MultiImageView;
import org.floens.chan.ui.view.ViewPagerAdapter;

import java.util.List;

public class ImageViewerAdapter extends ViewPagerAdapter {
    private final Context context;
    private final List<PostImage> images;
    private final MultiImageView.Callback multiImageViewCallback;

    public ImageViewerAdapter(Context context, List<PostImage> images, MultiImageView.Callback multiImageViewCallback) {
        this.context = context;
        this.images = images;
        this.multiImageViewCallback = multiImageViewCallback;
    }

    @Override
    public View getView(int position) {
        MultiImageView view = new MultiImageView(context);
        view.bindPostImage(images.get(position), multiImageViewCallback);

        return view;
    }

    @Override
    public int getCount() {
        return images.size();
    }
}

package org.floens.chan.core.presenter;

import org.floens.chan.core.model.PostImage;

import java.util.List;

public class ImageViewerPresenter {
    private final Callback callback;

    private boolean exiting = false;
    private List<PostImage> images;
    private int selectedIndex;

    public ImageViewerPresenter(Callback callback) {
        this.callback = callback;
    }

    public void showImages(List<PostImage> images, int index) {
        callback.startPreviewInTransition();

        this.images = images;
        selectedIndex = index;
    }

    public void onExit() {
        if (exiting) return;
        exiting = true;
        callback.startPreviewOutTransition();
    }

    public void onInTransitionEnd() {
        PostImage image = images.get(selectedIndex);
    }

    public interface Callback {
        public void startPreviewInTransition();

        public void startPreviewOutTransition();
    }
}

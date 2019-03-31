package org.floens.chan.ui.helper;

import android.content.Context;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.ui.controller.ImageReencodingController;

public class ImageReencodingHelper {
    private Context context;
    private ImageReencodingController imageReencodingController;
    private final ImageReencodingHelperCallback callback;

    public ImageReencodingHelper(Context context, ImageReencodingHelperCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void showController(Loadable loadable) {
        if (imageReencodingController == null) {
            imageReencodingController = new ImageReencodingController(context, this, loadable);
            callback.presentImageReencodingController(imageReencodingController);
        }
    }

    public void pop() {
        if (imageReencodingController != null) {
            imageReencodingController.stopPresenting();
            imageReencodingController = null;
        }
    }

    public interface ImageReencodingHelperCallback {
        void presentImageReencodingController(Controller controller);
    }
}

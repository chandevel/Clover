package org.floens.chan.ui.helper;

import android.content.Context;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.presenter.ImageReencodingPresenter;
import org.floens.chan.ui.controller.ImageOptionsController;
import org.floens.chan.ui.controller.ImageReencodeOptionsController;

public class ImageOptionsHelper implements
        ImageOptionsController.ImageOptionsControllerCallbacks,
        ImageReencodeOptionsController.ImageReencodeOptionsCallbacks {
    private Context context;
    private ImageOptionsController imageOptionsController = null;
    private ImageReencodeOptionsController imageReencodeOptionsController = null;
    private final ImageReencodingHelperCallback callback;

    public ImageOptionsHelper(Context context, ImageReencodingHelperCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void showController(Loadable loadable) {
        if (imageOptionsController == null) {
            imageOptionsController = new ImageOptionsController(context, this, this, loadable);
            callback.presentController(imageOptionsController);
        }
    }

    public void pop() {
        //first we have to pop the imageReencodeOptionsController
        if (imageReencodeOptionsController != null) {
            imageReencodeOptionsController.stopPresenting();
            imageReencodeOptionsController = null;
            return;
        }

        if (imageOptionsController != null) {
            imageOptionsController.stopPresenting();
            imageOptionsController = null;
        }
    }

    @Override
    public void onReencodeOptionClicked() {
        if (imageReencodeOptionsController == null) {
            imageReencodeOptionsController = new ImageReencodeOptionsController(context, this, this);
            callback.presentController(imageReencodeOptionsController);
        }
    }

    @Override
    public void onCanceled() {
        if (imageOptionsController != null) {
            imageOptionsController.onReencodingCanceled();
        }

        pop();
    }

    @Override
    public void onOk(ImageReencodingPresenter.Reencode reencode) {
        if (imageOptionsController != null) {
            imageOptionsController.onReencodeOptionsSet(reencode);
        }

        pop();
    }

    public interface ImageReencodingHelperCallback {
        void presentController(Controller controller);
    }
}

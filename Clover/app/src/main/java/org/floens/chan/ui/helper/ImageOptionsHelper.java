package org.floens.chan.ui.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.support.annotation.Nullable;

import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.presenter.ImageReencodingPresenter;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.ui.controller.ImageOptionsController;
import org.floens.chan.ui.controller.ImageReencodeOptionsController;

public class ImageOptionsHelper implements
        ImageOptionsController.ImageOptionsControllerCallbacks,
        ImageReencodeOptionsController.ImageReencodeOptionsCallbacks {
    private Context context;
    private ImageOptionsController imageOptionsController = null;
    private ImageReencodeOptionsController imageReencodeOptionsController = null;
    private final ImageReencodingHelperCallback callbacks;

    public ImageOptionsHelper(Context context, ImageReencodingHelperCallback callbacks) {
        this.context = context;
        this.callbacks = callbacks;
    }

    public void showController(Loadable loadable) {
        if (imageOptionsController == null) {
            imageOptionsController = new ImageOptionsController(context, this, this, loadable);
            callbacks.presentController(imageOptionsController);
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
    public void onReencodeOptionClicked(@Nullable Bitmap.CompressFormat imageFormat) {
        if (imageReencodeOptionsController == null && imageFormat != null) {
            imageReencodeOptionsController = new ImageReencodeOptionsController(context, this, this, imageFormat);
            callbacks.presentController(imageReencodeOptionsController);
        }
    }

    @Override
    public void onImageOptionsApplied(Reply reply) {
        callbacks.onImageOptionsApplied(reply);
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
            if (reencode.isDefault()) {
                imageOptionsController.onReencodingCanceled();
            } else {
                imageOptionsController.onReencodeOptionsSet(reencode);
            }
        }

        pop();
    }

    public interface ImageReencodingHelperCallback {
        void presentController(Controller controller);
        void onImageOptionsApplied(Reply reply);
    }
}

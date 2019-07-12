package com.github.adamantcheese.chan.ui.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Pair;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.ui.controller.ImageOptionsController;
import com.github.adamantcheese.chan.ui.controller.ImageReencodeOptionsController;

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
    public void onReencodeOptionClicked(@Nullable Bitmap.CompressFormat imageFormat, @Nullable Pair<Integer, Integer> dims) {
        if (imageReencodeOptionsController == null && imageFormat != null && dims != null) {
            imageReencodeOptionsController = new ImageReencodeOptionsController(context, this, this, imageFormat, dims);
            callbacks.presentController(imageReencodeOptionsController);
        } else {
            Toast.makeText(context, context.getString(R.string.image_reencode_format_error), Toast.LENGTH_LONG).show();
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
    public void onOk(ImageReencodingPresenter.ReencodeSettings reencodeSettings) {
        if (imageOptionsController != null) {
            if (reencodeSettings.isDefault()) {
                imageOptionsController.onReencodingCanceled();
            } else {
                imageOptionsController.onReencodeOptionsSet(reencodeSettings);
            }
        }

        pop();
    }

    public interface ImageReencodingHelperCallback {
        void presentController(Controller controller);

        void onImageOptionsApplied(Reply reply);
    }
}

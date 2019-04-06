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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatImageView;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.Toast;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.presenter.ImageReencodingPresenter;
import org.floens.chan.core.site.http.Reply;
import org.floens.chan.ui.helper.ImageOptionsHelper;
import org.floens.chan.utils.AndroidUtils;

public class ImageOptionsController extends Controller implements
        View.OnClickListener,
        CompoundButton.OnCheckedChangeListener,
        ImageReencodingPresenter.ImageReencodingPresenterCallback {
    private final static String TAG = "ImageOptionsController";
    private static final int TRANSITION_DURATION = 200;

    private ImageReencodingPresenter presenter;
    private ImageOptionsHelper imageReencodingHelper;
    private ImageOptionsControllerCallbacks callbacks;

    private ConstraintLayout viewHolder;
    private AppCompatImageView preview;
    private AppCompatCheckBox removeMetadata;
    private AppCompatCheckBox removeFilename;
    private AppCompatCheckBox changeImageChecksum;
    private AppCompatCheckBox reencode;
    private AppCompatButton cancel;
    private AppCompatButton ok;

    private Loadable loadable;
    private int statusBarColorPrevious;

    public ImageOptionsController(
            Context context,
            ImageOptionsHelper imageReencodingHelper,
            ImageOptionsControllerCallbacks callbacks,
            Loadable loadable
    ) {
        super(context);
        this.imageReencodingHelper = imageReencodingHelper;
        this.callbacks = callbacks;
        this.loadable = loadable;

        presenter = new ImageReencodingPresenter(this, loadable);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.layout_image_options);

        viewHolder = view.findViewById(R.id.image_options_view_holder);
        preview = view.findViewById(R.id.image_options_preview);
        removeMetadata = view.findViewById(R.id.image_options_remove_metadata);
        changeImageChecksum = view.findViewById(R.id.image_options_change_image_checksum);
        removeFilename = view.findViewById(R.id.image_options_remove_filename);
        reencode = view.findViewById(R.id.image_options_reencode);
        cancel = view.findViewById(R.id.image_options_cancel);
        ok = view.findViewById(R.id.image_options_ok);

        removeMetadata.setOnCheckedChangeListener(this);
        removeFilename.setOnCheckedChangeListener(this);
        reencode.setOnCheckedChangeListener(this);
        changeImageChecksum.setOnCheckedChangeListener(this);

        viewHolder.setOnClickListener(this);
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);

        presenter.loadImagePreview();

        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = getWindow().getStatusBarColor();
            if (statusBarColorPrevious != 0) {
                AndroidUtils.animateStatusBar(getWindow(), true, statusBarColorPrevious, TRANSITION_DURATION);
            }
        }
    }

    @Override
    public void stopPresenting() {
        super.stopPresenting();

        if (Build.VERSION.SDK_INT >= 21) {
            if (statusBarColorPrevious != 0) {
                AndroidUtils.animateStatusBar(getWindow(), true, statusBarColorPrevious, TRANSITION_DURATION);
            }
        }
    }

    @Override
    public boolean onBack() {
        imageReencodingHelper.pop();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == cancel) {
            imageReencodingHelper.pop();
        } else if (v == ok) {
            presenter.applyImageOptions();
        } else if (v == viewHolder) {
            imageReencodingHelper.pop();
        } else {
            throw new RuntimeException("onClick Unknown view clicked");
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == changeImageChecksum) {
            presenter.changeImageChecksum(isChecked);
        } else if (buttonView == removeMetadata) {
            presenter.removeMetadata(isChecked);
        } else if (buttonView == removeFilename) {
            presenter.removeFilename(isChecked);
        } else if (buttonView == reencode) {
            //isChecked here means whether the current click has made the button checked
            if (!isChecked) {
                onReencodingCanceled();
            } else {
                callbacks.onReencodeOptionClicked();
            }
        } else {
            throw new RuntimeException("onCheckedChanged Unknown view clicked");
        }
    }

    public void onReencodingCanceled() {
        removeMetadata.setChecked(false);
        removeMetadata.setEnabled(true);
        reencode.setChecked(false);

        presenter.setReencode(null);
    }

    public void onReencodeOptionsSet(ImageReencodingPresenter.Reencode reencode) {
        removeMetadata.setChecked(true);
        removeMetadata.setEnabled(false);

        presenter.setReencode(reencode);
    }

    @Override
    public void showImagePreview(Bitmap bitmap) {
        preview.setImageBitmap(bitmap);
    }

    @Override
    public void showCouldNotDecodeBitmapError() {
        //called on the background thread!

        AndroidUtils.runOnUiThread(() -> {
            showToastMessage(context.getString(R.string.could_not_decode_image_bitmap));
        });
    }

    @Override
    public void onImageOptionsApplied(Reply reply) {
        //called on the background thread!

        AndroidUtils.runOnUiThread(() -> {
            imageReencodingHelper.pop();
            callbacks.onImageOptionsApplied(reply);
        });
    }

    @Override
    public void showFailedToReencodeImage(Throwable error) {
        //called on the background thread!

        AndroidUtils.runOnUiThread(() -> {
            String text = String.format(context.getString(R.string.could_not_apply_image_options), error.getMessage());
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void disableOrEnableButtons(boolean enabled) {
        //called on the background thread!

        AndroidUtils.runOnUiThread(() -> {
            removeMetadata.setEnabled(enabled);
            removeFilename.setEnabled(enabled);
            changeImageChecksum.setEnabled(enabled);
            reencode.setEnabled(enabled);
            viewHolder.setEnabled(enabled);
            cancel.setEnabled(enabled);
            ok.setEnabled(enabled);
        });
    }

    private void showToastMessage(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface ImageOptionsControllerCallbacks {
        void onReencodeOptionClicked();
        void onImageOptionsApplied(Reply reply);
    }
}

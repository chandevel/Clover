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
package com.github.adamantcheese.chan.ui.controller;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Pair;
import android.view.View;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;
import com.github.adamantcheese.chan.core.site.http.Reply;
import com.github.adamantcheese.chan.ui.helper.ImageOptionsHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDisplaySize;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AnimationUtils.animateStatusBar;
import static com.github.adamantcheese.chan.utils.BackgroundUtils.runOnUiThread;

public class ImageOptionsController
        extends Controller
        implements View.OnClickListener, CompoundButton.OnCheckedChangeListener,
                   ImageReencodingPresenter.ImageReencodingPresenterCallback {
    private final static String TAG = "ImageOptionsController";
    private static final int TRANSITION_DURATION = 200;

    private ImageReencodingPresenter presenter;
    private ImageOptionsHelper imageReencodingHelper;
    private ImageOptionsControllerCallbacks callbacks;

    private ConstraintLayout viewHolder;
    private CardView container;
    private LinearLayout optionsHolder;
    private ImageView preview;
    private AppCompatCheckBox fixExif;
    private AppCompatCheckBox removeMetadata;
    private AppCompatCheckBox removeFilename;
    private AppCompatCheckBox changeImageChecksum;
    private AppCompatCheckBox reencode;
    private AppCompatButton cancel;
    private AppCompatButton ok;

    private int statusBarColorPrevious;
    private ImageReencodingPresenter.ImageOptions lastSettings;
    private boolean ignoreSetup;
    private boolean reencodeEnabled;

    public ImageOptionsController(
            Context context,
            ImageOptionsHelper imageReencodingHelper,
            ImageOptionsControllerCallbacks callbacks,
            Loadable loadable,
            ImageReencodingPresenter.ImageOptions lastOptions,
            boolean supportsReencode
    ) {
        super(context);
        this.imageReencodingHelper = imageReencodingHelper;
        this.callbacks = callbacks;
        lastSettings = lastOptions;
        reencodeEnabled = supportsReencode;

        presenter = new ImageReencodingPresenter(this, loadable, lastOptions);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.layout_image_options);

        viewHolder = view.findViewById(R.id.image_options_view_holder);
        container = view.findViewById(R.id.container);
        optionsHolder = view.findViewById(R.id.reencode_options_group);
        preview = view.findViewById(R.id.image_options_preview);
        fixExif = view.findViewById(R.id.image_options_fix_exif);
        removeMetadata = view.findViewById(R.id.image_options_remove_metadata);
        changeImageChecksum = view.findViewById(R.id.image_options_change_image_checksum);
        removeFilename = view.findViewById(R.id.image_options_remove_filename);
        reencode = view.findViewById(R.id.image_options_reencode);
        cancel = view.findViewById(R.id.image_options_cancel);
        ok = view.findViewById(R.id.image_options_ok);

        fixExif.setOnCheckedChangeListener(this);
        removeMetadata.setOnCheckedChangeListener(this);
        removeFilename.setOnCheckedChangeListener(this);
        reencode.setOnCheckedChangeListener(this);
        changeImageChecksum.setOnCheckedChangeListener(this);

        //setup last settings first before checking other conditions to enable/disable stuff
        if (lastSettings != null) {
            ignoreSetup = true; //this variable is to ignore any side effects of checking all these boxes
            removeFilename.setChecked(lastSettings.getRemoveFilename());
            changeImageChecksum.setChecked(lastSettings.getChangeImageChecksum());
            fixExif.setChecked(lastSettings.getFixExif());
            ImageReencodingPresenter.ReencodeSettings lastReencode = lastSettings.getReencodeSettings();
            if (lastReencode != null && presenter.hasAttachedFile()) {
                removeMetadata.setChecked(!lastReencode.isDefault());
                removeMetadata.setEnabled(!lastReencode.isDefault());
                reencode.setChecked(!lastReencode.isDefault());
                reencode.setText(String.format("Re-encode %s", lastReencode.prettyPrint(presenter.getImageFormat())));
            } else {
                removeMetadata.setChecked(lastSettings.getRemoveMetadata());
            }
            ignoreSetup = false;
        }

        if (presenter.getImageFormat() != Bitmap.CompressFormat.JPEG) {
            fixExif.setChecked(false);
            fixExif.setEnabled(false);
            fixExif.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            fixExif.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
        }

        if (!reencodeEnabled) {
            changeImageChecksum.setChecked(false);
            changeImageChecksum.setEnabled(false);
            changeImageChecksum.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            changeImageChecksum.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            fixExif.setChecked(false);
            fixExif.setEnabled(false);
            fixExif.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            fixExif.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            removeMetadata.setChecked(false);
            removeMetadata.setEnabled(false);
            removeMetadata.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            removeMetadata.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            reencode.setChecked(false);
            reencode.setEnabled(false);
            reencode.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
            reencode.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
        }

        viewHolder.setOnClickListener(this);
        preview.setOnClickListener(v -> {
            boolean isCurrentlyVisible = optionsHolder.getVisibility() == VISIBLE;
            optionsHolder.setVisibility(isCurrentlyVisible ? GONE : VISIBLE);
            Point p = getDisplaySize();
            int dimX1 = isCurrentlyVisible ? p.x : MATCH_PARENT;
            int dimY1 = isCurrentlyVisible ? p.y : dp(300);
            preview.setLayoutParams(new LinearLayout.LayoutParams(dimX1, dimY1, 0));
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) container.getLayoutParams();
            params.width = isCurrentlyVisible ? p.x : dp(300);
            params.height = WRAP_CONTENT;
            container.setLayoutParams(params);
        });
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);

        presenter.loadImagePreview();

        statusBarColorPrevious = getWindow().getStatusBarColor();
        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(), true, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    @Override
    public void stopPresenting() {
        super.stopPresenting();

        if (statusBarColorPrevious != 0) {
            animateStatusBar(getWindow(), false, statusBarColorPrevious, TRANSITION_DURATION);
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
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView == changeImageChecksum) {
            presenter.changeImageChecksum(isChecked);
        } else if (buttonView == fixExif) {
            presenter.fixExif(isChecked);
        } else if (buttonView == removeMetadata) {
            presenter.removeMetadata(isChecked);
        } else if (buttonView == removeFilename) {
            presenter.removeFilename(isChecked);
        } else if (buttonView == reencode) {
            //isChecked here means whether the current click has made the button checked
            if (!ignoreSetup) { //this variable is to ignore any side effects of checking boxes when last settings are being put in
                if (!isChecked) {
                    onReencodingCanceled();
                } else {
                    callbacks.onReencodeOptionClicked(presenter.getImageFormat(), presenter.getImageDims());
                }
            }
        }
    }

    public void onReencodingCanceled() {
        removeMetadata.setChecked(false);
        removeMetadata.setEnabled(true);
        removeMetadata.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));
        removeMetadata.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));
        reencode.setChecked(false);

        reencode.setText(getString(R.string.image_options_re_encode));

        presenter.setReencode(null);
    }

    public void onReencodeOptionsSet(ImageReencodingPresenter.ReencodeSettings reencodeSettings) {
        removeMetadata.setChecked(true);
        removeMetadata.setEnabled(false);
        removeMetadata.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));
        removeMetadata.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textSecondary));

        reencode.setText(String.format("Re-encode %s", reencodeSettings.prettyPrint(presenter.getImageFormat())));

        presenter.setReencode(reencodeSettings);
    }

    @Override
    public void showImagePreview(Bitmap bitmap) {
        preview.setImageBitmap(bitmap);
    }

    @Override
    public void onImageOptionsApplied(Reply reply, boolean filenameRemoved) {
        //called on the background thread!

        runOnUiThread(() -> {
            imageReencodingHelper.pop();
            callbacks.onImageOptionsApplied(reply, filenameRemoved);
        });
    }

    @Override
    public void disableOrEnableButtons(boolean enabled) {
        //called on the background thread!

        runOnUiThread(() -> {
            fixExif.setEnabled(enabled);
            removeMetadata.setEnabled(enabled);
            removeFilename.setEnabled(enabled);
            changeImageChecksum.setEnabled(enabled);
            reencode.setEnabled(enabled);
            viewHolder.setEnabled(enabled);
            cancel.setEnabled(enabled);
            ok.setEnabled(enabled);
        });
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface ImageOptionsControllerCallbacks {
        void onReencodeOptionClicked(
                @Nullable Bitmap.CompressFormat imageFormat, @Nullable Pair<Integer, Integer> dims
        );

        void onImageOptionsApplied(Reply reply, boolean filenameRemoved);
    }
}

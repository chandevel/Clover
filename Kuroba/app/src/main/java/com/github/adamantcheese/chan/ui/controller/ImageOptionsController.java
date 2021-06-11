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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.BackgroundUtils;

import static android.graphics.Bitmap.CompressFormat.JPEG;
import static android.graphics.Bitmap.CompressFormat.PNG;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getWindowSize;

public class ImageOptionsController
        extends BaseFloatingController
        implements View.OnClickListener, ImageReencodingPresenter.ImageReencodingPresenterCallback,
                   RadioGroup.OnCheckedChangeListener {

    private final ImageReencodingPresenter presenter;
    private final ImageOptionsControllerCallback callback;

    private ConstraintLayout viewHolder;
    private CardView container;
    private ImageView preview;

    private LinearLayout optionsGroup;
    private RadioGroup radioGroup;
    private LinearLayout qualityGroup;
    private SeekBar quality;
    private SeekBar reduce;
    private TextView currentImageQuality;
    private TextView currentImageReduce;

    private CheckBox changeImageChecksum;
    private CheckBox fixExif;

    private Button cancel;
    private Button ok;

    private ImageReencodingPresenter.ImageOptions lastOptions;
    private final Pair<Integer, Integer> dims;
    private final CompressFormat imageFormat;

    private final SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (seekBar == quality) {
                if (progress < 1) {
                    //for API <26; the quality can't be lower than 1
                    seekBar.setProgress(1);
                    progress = 1;
                }
                currentImageQuality.setText(getString(R.string.image_quality, progress));
            } else if (seekBar == reduce) {
                currentImageReduce.setText(getString(R.string.scale_reduce,
                        dims.first,
                        dims.second,
                        (int) (dims.first * ((100f - (float) progress) / 100f)),
                        (int) (dims.second * ((100f - (float) progress) / 100f)),
                        100 - progress
                ));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) { }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) { }
    };

    public ImageOptionsController(Context context, Loadable loadable, ImageOptionsControllerCallback callback) {
        super(context);
        this.callback = callback;
        try { //load up the last image options every time this controller is created
            lastOptions = AppModule.gson.fromJson(ChanSettings.lastImageOptions.get(),
                    ImageReencodingPresenter.ImageOptions.class
            );
        } catch (Exception e) {
            lastOptions = null;
        }

        presenter = new ImageReencodingPresenter(context, this, loadable);

        dims = presenter.getImageDims();
        imageFormat = presenter.getCurrentFileFormat();

        // if for any reason the imageformat is null, we won't know what to do with it
        if (imageFormat == null) throw new IllegalStateException();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.layout_image_options;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        viewHolder = view.findViewById(R.id.image_options_view_holder);
        container = view.findViewById(R.id.container);
        optionsGroup = view.findViewById(R.id.reencode_options_group);
        preview = view.findViewById(R.id.image_options_preview);
        radioGroup = view.findViewById(R.id.reencode_image_radio_group);
        qualityGroup = view.findViewById(R.id.quality_group);
        quality = view.findViewById(R.id.reecode_image_quality);
        reduce = view.findViewById(R.id.reecode_image_reduce);
        currentImageQuality = view.findViewById(R.id.reecode_image_current_quality);
        currentImageReduce = view.findViewById(R.id.reecode_image_current_reduce);
        fixExif = view.findViewById(R.id.image_options_fix_exif);
        changeImageChecksum = view.findViewById(R.id.image_options_change_image_checksum);
        cancel = view.findViewById(R.id.image_options_cancel);
        ok = view.findViewById(R.id.image_options_ok);

        //setup last settings first before checking other conditions to enable/disable stuff
        if (lastOptions != null) {
            quality.setProgress(lastOptions.reencodeQuality);
            reduce.setProgress(lastOptions.reducePercent);
            changeImageChecksum.setChecked(lastOptions.changeImageChecksum);
            fixExif.setChecked(lastOptions.fixExif);
        }

        ((TextView) view.findViewById(R.id.reencode_title)).setText(getString(R.string.reencode_image_re_encode_image_text,
                imageFormat.name()
        ));
        if (imageFormat != PNG) {
            qualityGroup.setVisibility(GONE);
            quality.setProgress(100);
            quality.setEnabled(false);
        }
        int lastReducePercent = lastOptions != null ? lastOptions.reducePercent : 0;
        currentImageReduce.setText(getString(R.string.scale_reduce,
                dims.first,
                dims.second,
                (int) (dims.first * ((100f - lastReducePercent) / 100f)),
                (int) (dims.second * ((100f - lastReducePercent) / 100f)),
                100 - lastReducePercent
        ));

        if (imageFormat != JPEG || !presenter.hasExif()) {
            fixExif.setChecked(false);
            fixExif.setVisibility(GONE);
        }

        viewHolder.setOnClickListener(this);
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);
        radioGroup.setOnCheckedChangeListener(this);

        radioGroup.check(imageFormat == PNG ? R.id.reencode_image_as_png : R.id.reencode_image_as_jpeg);

        quality.setOnSeekBarChangeListener(listener);
        reduce.setOnSeekBarChangeListener(listener);

        preview.setOnClickListener(v -> { // tap the preview to zoom it in to fullscreen and hide the options
            boolean isCurrentlyVisible = optionsGroup.getVisibility() == VISIBLE;
            // isCurrentlyVisible ? action fullscreened : action minimized
            optionsGroup.setVisibility(isCurrentlyVisible ? GONE : VISIBLE);
            int dimX = isCurrentlyVisible ? getWindowSize().x : MATCH_PARENT;
            int dimY = isCurrentlyVisible ? getWindowSize().y : dp(0);
            int weight = isCurrentlyVisible ? 0 : 1;
            preview.setLayoutParams(new LinearLayout.LayoutParams(dimX, dimY, weight));
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) container.getLayoutParams();
            params.width = isCurrentlyVisible ? WRAP_CONTENT : dp(300);
            params.height = WRAP_CONTENT;
            container.setLayoutParams(params);
        });
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);

        presenter.loadImagePreview();
    }

    @Override
    public boolean onBack() {
        callback.onImageOptionsComplete();
        stopPresenting();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == cancel || v == viewHolder) {
            BackgroundUtils.runOnMainThread(() -> {
                stopPresenting();
                callback.onImageOptionsComplete();
            });
        } else if (v == ok) {
            presenter.applyImageOptions(new ImageReencodingPresenter.ImageOptions(fixExif.isChecked(),
                    changeImageChecksum.isChecked(),
                    quality.getProgress(),
                    reduce.getProgress()
            ));
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        // when re-encoding as png it ignores the compress quality option so we can just disable the quality seekbar
        if (checkedId == R.id.reencode_image_as_png) {
            qualityGroup.setVisibility(GONE);
            quality.setProgress(100);
            quality.setEnabled(false);
        } else if (checkedId == R.id.reencode_image_as_jpeg) {
            qualityGroup.setVisibility(VISIBLE);
            quality.setEnabled(true);
        }
    }

    @Override
    public void showImagePreview(Bitmap bitmap) {
        preview.setImageBitmap(bitmap);
    }

    @Override
    public void onImageOptionsApplied() {
        stopPresenting();
        callback.onImageOptionsApplied();
        callback.onImageOptionsComplete();
    }

    @Override
    public void disableOrEnableButtons(boolean enabled) {
        fixExif.setEnabled(enabled);
        changeImageChecksum.setEnabled(enabled);
        viewHolder.setEnabled(enabled);
        cancel.setEnabled(enabled);
        ok.setEnabled(enabled);
    }

    @Override
    public CompressFormat getReencodeFormat() {
        switch (radioGroup.getCheckedRadioButtonId()) {
            case R.id.reencode_image_as_jpeg:
                return JPEG;
            case R.id.reencode_image_as_png:
                return PNG;
        }
        return imageFormat;
    }

    public interface ImageOptionsControllerCallback {
        void onImageOptionsApplied();

        void onImageOptionsComplete();
    }
}

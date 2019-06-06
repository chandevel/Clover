package com.github.adamantcheese.chan.ui.controller;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatRadioButton;
import androidx.appcompat.widget.AppCompatSeekBar;
import android.view.View;
import android.view.Window;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.presenter.ImageReencodingPresenter;
import com.github.adamantcheese.chan.ui.helper.ImageOptionsHelper;
import com.github.adamantcheese.chan.utils.AndroidUtils;

public class ImageReencodeOptionsController extends Controller implements
        View.OnClickListener, RadioGroup.OnCheckedChangeListener {
    private final static String TAG = "ImageReencodeOptionsController";
    private static final int TRANSITION_DURATION = 200;

    private ImageReencodeOptionsCallbacks callbacks;
    private ImageOptionsHelper imageReencodingHelper;
    private Bitmap.CompressFormat imageFormat;

    private ConstraintLayout viewHolder;
    private RadioGroup radioGroup;
    private AppCompatSeekBar quality;
    private AppCompatSeekBar reduce;
    private TextView currentImageQuality;
    private TextView currentImageReduce;
    private AppCompatButton cancel;
    private AppCompatButton ok;
    private AppCompatRadioButton reencodeImageAsIs;

    private int statusBarColorPrevious;

    private SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (seekBar == quality) {
                currentImageQuality.setText(String.format(context.getString(R.string.image_quality), progress));
            } else if (seekBar == reduce) {
                currentImageReduce.setText(String.format(context.getString(R.string.scale_reduce), progress));
            } else {
                throw new RuntimeException("Unknown seekBar");
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            //do nothing
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            //do nothing
        }
    };

    public ImageReencodeOptionsController(
            Context context,
            ImageOptionsHelper imageReencodingHelper,
            ImageReencodeOptionsCallbacks callbacks,
            Bitmap.CompressFormat imageFormat
    ) {
        super(context);

        this.imageReencodingHelper = imageReencodingHelper;
        this.callbacks = callbacks;
        this.imageFormat = imageFormat;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.layout_image_reencoding);

        viewHolder = view.findViewById(R.id.reencode_image_view_holder);
        radioGroup = view.findViewById(R.id.reencode_image_radio_group);
        quality = view.findViewById(R.id.reecode_image_quality);
        reduce = view.findViewById(R.id.reecode_image_reduce);
        currentImageQuality = view.findViewById(R.id.reecode_image_current_quality);
        currentImageReduce = view.findViewById(R.id.reecode_image_current_reduce);
        reencodeImageAsIs = view.findViewById(R.id.reencode_image_as_is);
        AppCompatRadioButton reencodeImageAsJpeg = view.findViewById(R.id.reencode_image_as_jpeg);
        AppCompatRadioButton reencodeImageAsPng = view.findViewById(R.id.reencode_image_as_png);
        cancel = view.findViewById(R.id.reencode_image_cancel);
        ok = view.findViewById(R.id.reencode_image_ok);

        viewHolder.setOnClickListener(this);
        cancel.setOnClickListener(this);
        ok.setOnClickListener(this);
        radioGroup.setOnCheckedChangeListener(this);

        quality.setOnSeekBarChangeListener(listener);
        reduce.setOnSeekBarChangeListener(listener);

        setReencodeImageAsIsText();

        if (imageFormat == Bitmap.CompressFormat.PNG) {
            quality.setEnabled(false);
            reencodeImageAsPng.setEnabled(false);
        } else if (imageFormat == Bitmap.CompressFormat.JPEG) {
            reencodeImageAsJpeg.setEnabled(false);
        }

        statusBarColorPrevious = getWindow().getStatusBarColor();
        if (statusBarColorPrevious != 0) {
            AndroidUtils.animateStatusBar(getWindow(), true, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    private void setReencodeImageAsIsText() {
        String format;

        if (imageFormat == Bitmap.CompressFormat.PNG) {
            format = "PNG";
        } else if (imageFormat == Bitmap.CompressFormat.JPEG) {
            format = "JPEG";
        } else {
            format = "Unknown";
        }

        reencodeImageAsIs.setText(String.format(context.getString(R.string.reencode_image_as_is), format));
    }

    @Override
    public void stopPresenting() {
        super.stopPresenting();

        if (statusBarColorPrevious != 0) {
            AndroidUtils.animateStatusBar(getWindow(), false, statusBarColorPrevious, TRANSITION_DURATION);
        }
    }

    @Override
    public boolean onBack() {
        imageReencodingHelper.pop();
        return true;
    }

    @Override
    public void onClick(View v) {
        if (v == ok) {
            callbacks.onOk(getReencode());
        } else if (v == cancel || v == viewHolder) {
            callbacks.onCanceled();
        } else {
            throw new RuntimeException("onClick Unknown view clicked");
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        int index = group.indexOfChild(group.findViewById(group.getCheckedRadioButtonId()));

        // 0 - AS IS
        // 1 - AS JPEG
        // 2 - AS PNG

        // when re-encoding image as png it ignores the compress quality option so we can just
        // disable the quality seekbar
        if (index == 2 || (index == 0 && imageFormat == Bitmap.CompressFormat.PNG)) {
            quality.setProgress(100);
            quality.setEnabled(false);
        } else {
            quality.setEnabled(true);
        }
    }

    private ImageReencodingPresenter.Reencode getReencode() {
        int index = radioGroup.indexOfChild(radioGroup.findViewById(radioGroup.getCheckedRadioButtonId()));
        ImageReencodingPresenter.ReencodeType reencodeType = ImageReencodingPresenter.ReencodeType.fromInt(index);

        return new ImageReencodingPresenter.Reencode(
                reencodeType,
                quality.getProgress(),
                reduce.getProgress()
        );
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface ImageReencodeOptionsCallbacks {
        void onCanceled();
        void onOk(ImageReencodingPresenter.Reencode reencode);
    }
}

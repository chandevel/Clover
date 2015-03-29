package org.floens.chan.ui.view;

import android.content.Context;
import android.util.AttributeSet;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.floens.chan.utils.Logger;

public class CustomScaleImageView extends SubsamplingScaleImageView {
    private static final String TAG = "CustomScaleImageView";

    private Callback callback;

    public CustomScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
        init();
    }

    public CustomScaleImageView(Context context) {
        super(context);
        init();
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void init() {
        setOnImageEventListener(new OnImageEventListener() {
            @Override
            public void onReady() {
                float scale = Math.min(getWidth() / (float) getSWidth(), getHeight() / (float) getSHeight());
                setMinScale(scale);

                if (getMaxScale() < scale * 2f) {
                    setMaxScale(scale * 2f);
                }
                setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CUSTOM);

                if (callback != null) {
                    callback.onReady();
                }
            }

            @Override
            public void onImageLoaded() {
            }

            @Override
            public void onPreviewLoadError(Exception e) {
            }

            @Override
            public void onImageLoadError(Exception e) {
                Logger.w(TAG, "onImageLoadError", e);
                if (callback != null) {
                    callback.onError(true);
                }
            }

            @Override
            public void onTileLoadError(Exception e) {
                Logger.w(TAG, "onTileLoadError", e);
                if (callback != null) {
                    callback.onError(false);
                }
            }
        });
    }

    public interface Callback {
        public void onReady();
        public void onError(boolean wasInitial);
    }
}

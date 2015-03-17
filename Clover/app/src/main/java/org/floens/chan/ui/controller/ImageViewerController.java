package org.floens.chan.ui.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.presenter.ImageViewerPresenter;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.ui.view.ClippingImageView;
import org.floens.chan.utils.AnimationUtils;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AnimationUtils.calculateBoundsAnimation;

public class ImageViewerController extends Controller implements View.OnClickListener, ImageViewerPresenter.Callback {
    private static final int TRANSITION_DURATION = 200; //165;
    private static final int TRANSITION_CLIP_DURATION = (int) (TRANSITION_DURATION * 0.5f);
    private static final float TRANSITION_FINAL_ALPHA = 0.80f;

    private int statusBarColorPrevious;
    private AnimatorSet startPreviewAnimation;
    private AnimatorSet endAnimation;

    private Callback callback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private ClippingImageView previewImage;
    private ViewPager pager;

    public ImageViewerController(Context context, Toolbar toolbar) {
        super(context);
        this.toolbar = toolbar;

        presenter = new ImageViewerPresenter(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = "Image title here";

        view = inflateRes(R.layout.controller_image_viewer);
        view.setOnClickListener(this);
        previewImage = (ClippingImageView) view.findViewById(R.id.preview_image);
        pager = (ViewPager) view.findViewById(R.id.pager);
    }

    @Override
    public void onClick(View v) {
        presenter.onExit();
    }

    @Override
    public boolean onBack() {
        presenter.onExit();
        return true;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public ImageViewerPresenter getPresenter() {
        return presenter;
    }

    public void startPreviewInTransition() {
        ImageView previewImageView = callback.getPreviewImageStartView(this);
        previewImage.setImageDrawable(previewImageView.getDrawable());

        Rect startBounds = getImageViewBounds(previewImageView);
        final Rect endBounds = new Rect();
        final Point globalOffset = new Point();
        view.getGlobalVisibleRect(endBounds, globalOffset);
        float startScale = calculateBoundsAnimation(startBounds, endBounds, globalOffset);

        previewImage.setPivotX(0f);
        previewImage.setPivotY(0f);
        previewImage.setX(startBounds.left);
        previewImage.setY(startBounds.top);
        previewImage.setScaleX(startScale);
        previewImage.setScaleY(startScale);

        Rect clipStartBounds = new Rect(0, 0, (int) (previewImageView.getWidth() / startScale), (int) (previewImageView.getHeight() / startScale));

        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = getWindow().getStatusBarColor();
        }

        doPreviewInTransition(startBounds, endBounds, startScale, clipStartBounds);
    }

    public void startPreviewOutTransition() {
        if (startPreviewAnimation != null || endAnimation != null) {
            return;
        }

        doPreviewOutAnimation();
    }

    private void doPreviewInTransition(Rect startBounds, Rect finalBounds, float startScale, final Rect clipStartBounds) {
        startPreviewAnimation = new AnimatorSet();

        ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(0f, 1f);
        backgroundAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setBackgroundAlpha((float) animation.getAnimatedValue());
            }
        });

        final Rect clipRect = new Rect();
        ValueAnimator clip = ValueAnimator.ofFloat(1f, 0f);
        clip.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                AnimationUtils.getClippingBounds(clipStartBounds, previewImage, clipRect, (float) animation.getAnimatedValue());
                previewImage.clip(clipRect);
            }
        });

        startPreviewAnimation
                .play(ObjectAnimator.ofFloat(previewImage, View.X, startBounds.left, finalBounds.left).setDuration(TRANSITION_DURATION))
                .with(ObjectAnimator.ofFloat(previewImage, View.Y, startBounds.top, finalBounds.top).setDuration(TRANSITION_DURATION))
                .with(ObjectAnimator.ofFloat(previewImage, View.SCALE_X, startScale, 1f).setDuration(TRANSITION_DURATION))
                .with(ObjectAnimator.ofFloat(previewImage, View.SCALE_Y, startScale, 1f).setDuration(TRANSITION_DURATION))
                .with(backgroundAlpha.setDuration(TRANSITION_DURATION))
                .with(clip.setDuration(TRANSITION_CLIP_DURATION));

        startPreviewAnimation.setInterpolator(new DecelerateInterpolator());
        startPreviewAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                callback.onPreviewCreate(ImageViewerController.this);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                previewImage.setX(0f);
                previewImage.setY(0f);
                previewImage.setScaleX(1f);
                previewImage.setScaleY(1f);
                previewImage.clip(null);
                startPreviewAnimation = null;
                presenter.onInTransitionEnd();
            }
        });
        startPreviewAnimation.start();
    }

    private void doPreviewOutAnimation() {
        ImageView startImage = getStartImageView();
        Rect startBounds = null;
        if (startImage != null) {
            startBounds = getImageViewBounds(startImage);
        }
        if (startBounds == null) {
            endAnimation = new AnimatorSet();

            ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(1f, 0f);
            backgroundAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setBackgroundAlpha((float) animation.getAnimatedValue());
                }
            });

            endAnimation
                    .play(ObjectAnimator.ofFloat(previewImage, View.Y, previewImage.getTop(), previewImage.getTop() + dp(20)))
                    .with(ObjectAnimator.ofFloat(previewImage, View.ALPHA, 1f, 0f))
                    .with(backgroundAlpha);

            endAnimation.setDuration(TRANSITION_DURATION);
            endAnimation.setInterpolator(new DecelerateInterpolator());
            endAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    previewOutAnimationEnded();
                }
            });
            endAnimation.start();
        } else {
            final Rect endBounds = new Rect();
            final Point globalOffset = new Point();
            view.getGlobalVisibleRect(endBounds, globalOffset);
            float startScale = calculateBoundsAnimation(startBounds, endBounds, globalOffset);

            endAnimation = new AnimatorSet();

            ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(1f, 0f);
            backgroundAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setBackgroundAlpha((float) animation.getAnimatedValue());
                }
            });

            final Rect clipStartBounds = new Rect(0, 0, (int) (startImage.getWidth() / startScale), (int) (startImage.getHeight() / startScale));
            final Rect clipRect = new Rect();
            ValueAnimator clip = ValueAnimator.ofFloat(0f, 1f);
            clip.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    AnimationUtils.getClippingBounds(clipStartBounds, previewImage, clipRect, (float) animation.getAnimatedValue());
                    previewImage.clip(clipRect);
                }
            });
            clip.setStartDelay(TRANSITION_DURATION - TRANSITION_CLIP_DURATION);
            clip.setDuration(TRANSITION_CLIP_DURATION);

            endAnimation
                    .play(ObjectAnimator.ofFloat(previewImage, View.X, startBounds.left).setDuration(TRANSITION_DURATION))
                    .with(ObjectAnimator.ofFloat(previewImage, View.Y, startBounds.top).setDuration(TRANSITION_DURATION))
                    .with(ObjectAnimator.ofFloat(previewImage, View.SCALE_X, 1f, startScale).setDuration(TRANSITION_DURATION))
                    .with(ObjectAnimator.ofFloat(previewImage, View.SCALE_Y, 1f, startScale).setDuration(TRANSITION_DURATION))
                    .with(backgroundAlpha.setDuration(TRANSITION_DURATION))
                    .with(clip);

            endAnimation.setInterpolator(new DecelerateInterpolator());
            endAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    previewOutAnimationEnded();
                }
            });
            endAnimation.start();
        }
    }

    private void previewOutAnimationEnded() {
        setBackgroundAlpha(0f);

        callback.onPreviewDestroy(this);
        navigationController.stopPresenting(false);
    }

    private void setBackgroundAlpha(float alpha) {
        view.setBackgroundColor(Color.argb((int) (alpha * TRANSITION_FINAL_ALPHA * 255f), 0, 0, 0));

        if (Build.VERSION.SDK_INT >= 21) {
            if (alpha == 0f) {
                setStatusBarColor(statusBarColorPrevious);
            } else {
                int r = (int) ((1f - alpha) * Color.red(statusBarColorPrevious));
                int g = (int) ((1f - alpha) * Color.green(statusBarColorPrevious));
                int b = (int) ((1f - alpha) * Color.blue(statusBarColorPrevious));
                setStatusBarColor(Color.argb(255, r, g, b));
            }
        }

        setToolbarBackgroundAlpha(alpha);
    }

    private void setStatusBarColor(int color) {
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(color);
        }
    }

    private void setToolbarBackgroundAlpha(float alpha) {
        toolbar.setAlpha(alpha);
    }

    private Rect getImageViewBounds(ImageView image) {
        Rect startBounds = new Rect();
        if (image.getGlobalVisibleRect(startBounds) && !startBounds.isEmpty()) {
            AnimationUtils.adjustImageViewBoundsToDrawableBounds(image, startBounds);
            if (!startBounds.isEmpty()) {
                return startBounds;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private ImageView getStartImageView() {
        return callback.getPreviewImageStartView(this);
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface Callback {
        public ImageView getPreviewImageStartView(ImageViewerController imageViewerController);

        public void onPreviewCreate(ImageViewerController imageViewerController);

        public void onPreviewDestroy(ImageViewerController imageViewerController);
    }
}

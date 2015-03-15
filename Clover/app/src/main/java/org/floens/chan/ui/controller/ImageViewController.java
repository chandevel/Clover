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
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.ui.view.ClippingImageView;
import org.floens.chan.utils.AnimationUtils;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AnimationUtils.calculateBoundsAnimation;

public class ImageViewController extends Controller implements View.OnClickListener {
    private static final int DURATION = 165;
    private static final float CLIP_DURATION_PERCENTAGE = 0.40f;
    private static final float FINAL_ALPHA = 0.80f;

    private ClippingImageView imageView;
    private Callback callback;

    private int statusBarColorPrevious;
    private AnimatorSet startAnimation;
    private AnimatorSet endAnimation;

    public ImageViewController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_view_image);

        imageView = (ClippingImageView) view.findViewById(R.id.image);
        view.setOnClickListener(this);
    }

    @Override
    public boolean onBack() {
        removeImage();
        return true;
    }

    @Override
    public void onClick(View v) {
        removeImage();
    }

    public void setImage(Callback callback, final ImageView startImageView) {
        this.callback = callback;

        imageView.setImageDrawable(startImageView.getDrawable());

        Rect startBounds = getStartImageViewBounds(startImageView);
        final Rect endBounds = new Rect();
        final Point globalOffset = new Point();
        view.getGlobalVisibleRect(endBounds, globalOffset);
        float startScale = calculateBoundsAnimation(startBounds, endBounds, globalOffset);

        imageView.setPivotX(0f);
        imageView.setPivotY(0f);
        imageView.setX(startBounds.left);
        imageView.setY(startBounds.top);
        imageView.setScaleX(startScale);
        imageView.setScaleY(startScale);

        Rect clipStartBounds = new Rect(0, 0, (int) (startImageView.getWidth() / startScale), (int) (startImageView.getHeight() / startScale));

        Window window = ((Activity) context).getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = window.getStatusBarColor();
        }

        startAnimation(startBounds, endBounds, startScale, clipStartBounds);
    }

    public void removeImage() {
        if (startAnimation != null || endAnimation != null) {
            return;
        }

        endAnimation();
//        endAnimationEmpty();
    }

    private void startAnimation(Rect startBounds, Rect finalBounds, float startScale, final Rect clipStartBounds) {
        startAnimation = new AnimatorSet();

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
                AnimationUtils.getClippingBounds(clipStartBounds, imageView, clipRect, (float) animation.getAnimatedValue());
                imageView.clip(clipRect);
            }
        });

        startAnimation
                .play(ObjectAnimator.ofFloat(imageView, View.X, startBounds.left, finalBounds.left).setDuration(DURATION))
                .with(ObjectAnimator.ofFloat(imageView, View.Y, startBounds.top, finalBounds.top).setDuration(DURATION))
                .with(ObjectAnimator.ofFloat(imageView, View.SCALE_X, startScale, 1f).setDuration(DURATION))
                .with(ObjectAnimator.ofFloat(imageView, View.SCALE_Y, startScale, 1f).setDuration(DURATION))
                .with(backgroundAlpha.setDuration(DURATION))
                .with(clip.setDuration((long) (DURATION * CLIP_DURATION_PERCENTAGE)));

        startAnimation.setInterpolator(new DecelerateInterpolator());
        startAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startAnimationEnd();
                startAnimation = null;
            }
        });
        startAnimation.start();
    }

    private void startAnimationEnd() {
        imageView.setX(0f);
        imageView.setY(0f);
        imageView.setScaleX(1f);
        imageView.setScaleY(1f);
    }

    private void endAnimation() {
        ImageView startImage = getStartImageView();
        Rect startBounds = null;
        if (startImage != null) {
            startBounds = getStartImageViewBounds(startImage);
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
                    .play(ObjectAnimator.ofFloat(imageView, View.Y, imageView.getTop(), imageView.getTop() + dp(20)))
                    .with(ObjectAnimator.ofFloat(imageView, View.ALPHA, 1f, 0f))
                    .with(backgroundAlpha);

            endAnimation.setDuration(DURATION);
            endAnimation.setInterpolator(new DecelerateInterpolator());
            endAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    endAnimationEnd();
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
                    AnimationUtils.getClippingBounds(clipStartBounds, imageView, clipRect, (float) animation.getAnimatedValue());
                    imageView.clip(clipRect);
                }
            });
            long clipDuration = (long) (DURATION * CLIP_DURATION_PERCENTAGE);
            clip.setStartDelay(DURATION - clipDuration);
            clip.setDuration(clipDuration);

            endAnimation
                    .play(ObjectAnimator.ofFloat(imageView, View.X, startBounds.left).setDuration(DURATION))
                    .with(ObjectAnimator.ofFloat(imageView, View.Y, startBounds.top).setDuration(DURATION))
                    .with(ObjectAnimator.ofFloat(imageView, View.SCALE_X, 1f, startScale).setDuration(DURATION))
                    .with(ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 1f, startScale).setDuration(DURATION))
                    .with(backgroundAlpha.setDuration(DURATION))
                    .with(clip);

            endAnimation.setInterpolator(new DecelerateInterpolator());
            endAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    endAnimationEnd();
                }
            });
            endAnimation.start();
        }
    }

    private void endAnimationEnd() {
        Window window = ((Activity) context).getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(statusBarColorPrevious);
        }

        callback.onImageViewLayoutDestroy(this);
        stopPresenting(false);
    }

    private void setBackgroundAlpha(float alpha) {
        alpha = alpha * FINAL_ALPHA;
        view.setBackgroundColor(Color.argb((int) (alpha * 255f), 0, 0, 0));

        if (Build.VERSION.SDK_INT >= 21) {
            Window window = ((Activity) context).getWindow();

            int r = (int) ((1f - alpha) * Color.red(statusBarColorPrevious));
            int g = (int) ((1f - alpha) * Color.green(statusBarColorPrevious));
            int b = (int) ((1f - alpha) * Color.blue(statusBarColorPrevious));

            window.setStatusBarColor(Color.argb(255, r, g, b));
        }
    }

    private Rect getStartImageViewBounds(ImageView image) {
        Rect startBounds = new Rect();
        if (image.getGlobalVisibleRect(startBounds)) {
            AnimationUtils.adjustImageViewBoundsToDrawableBounds(image, startBounds);
            return startBounds;
        } else {
            return null;
        }
    }

    private ImageView getStartImageView() {
        return callback.getImageView(this);
    }

    public interface Callback {
        public ImageView getImageView(ImageViewController imageViewController);

        public void onImageViewLayoutDestroy(ImageViewController imageViewController);
    }
}

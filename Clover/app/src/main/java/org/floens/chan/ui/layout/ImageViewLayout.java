package org.floens.chan.ui.layout;

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
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.floens.chan.R;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AnimationUtils.calculateBoundsAnimation;


public class ImageViewLayout extends FrameLayout implements View.OnClickListener {
    private ImageView imageView;

    private Callback callback;
    private Drawable drawable;

    private int statusBarColorPrevious;
    private AnimatorSet startAnimation;
    private AnimatorSet endAnimation;

    public static ImageViewLayout attach(Window window) {
        ImageViewLayout imageViewLayout = (ImageViewLayout) LayoutInflater.from(window.getContext()).inflate(R.layout.image_view_layout, null);
        window.addContentView(imageViewLayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return imageViewLayout;
    }

    public ImageViewLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        this.imageView = (ImageView) findViewById(R.id.image);
        setOnClickListener(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        super.onTouchEvent(event);
        return true;
    }

    @Override
    public void onClick(View v) {
        removeImage();
    }

    public void setImage(Callback callback, final Drawable drawable) {
        this.callback = callback;
        this.drawable = drawable;

        this.imageView.setImageDrawable(drawable);

        Rect startBounds = callback.getImageViewLayoutStartBounds();
        final Rect endBounds = new Rect();
        final Point globalOffset = new Point();
        getGlobalVisibleRect(endBounds, globalOffset);
        float startScale = calculateBoundsAnimation(startBounds, endBounds, globalOffset);

        imageView.setPivotX(0f);
        imageView.setPivotY(0f);
        imageView.setX(startBounds.left);
        imageView.setY(startBounds.top);
        imageView.setScaleX(startScale);
        imageView.setScaleY(startScale);

        Window window = ((Activity) getContext()).getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = window.getStatusBarColor();
        }

        startAnimation(startBounds, endBounds, startScale);
    }

    public void removeImage() {
        if (startAnimation != null || endAnimation != null) {
            return;
        }

        endAnimation();
//        endAnimationEmpty();
    }

    private void startAnimation(Rect startBounds, Rect finalBounds, float startScale) {
        startAnimation = new AnimatorSet();

        ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(0f, 1f);
        backgroundAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setBackgroundAlpha((float) animation.getAnimatedValue());
            }
        });

        startAnimation
                .play(ObjectAnimator.ofFloat(imageView, View.X, startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(imageView, View.Y, startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(imageView, View.SCALE_X, startScale, 1f))
                .with(ObjectAnimator.ofFloat(imageView, View.SCALE_Y, startScale, 1f))
                .with(backgroundAlpha);

        startAnimation.setDuration(200);
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
//        controller.setVisibility(false);
    }

    private void endAnimation() {
//        controller.setVisibility(true);

        Rect startBounds = callback.getImageViewLayoutStartBounds();
        final Rect endBounds = new Rect();
        final Point globalOffset = new Point();
        getGlobalVisibleRect(endBounds, globalOffset);
        float startScale = calculateBoundsAnimation(startBounds, endBounds, globalOffset);

        endAnimation = new AnimatorSet();

        ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(1f, 0f);
        backgroundAlpha.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setBackgroundAlpha((float) animation.getAnimatedValue());
            }
        });

        endAnimation
                .play(ObjectAnimator.ofFloat(imageView, View.X, startBounds.left))
                .with(ObjectAnimator.ofFloat(imageView, View.Y, startBounds.top))
                .with(ObjectAnimator.ofFloat(imageView, View.SCALE_X, 1f, startScale))
                .with(ObjectAnimator.ofFloat(imageView, View.SCALE_Y, 1f, startScale))
                .with(backgroundAlpha);

        endAnimation.setDuration(200);
        endAnimation.setInterpolator(new DecelerateInterpolator());
        endAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endAnimationEnd();
            }
        });
        endAnimation.start();
    }

    private void endAnimationEmpty() {
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

        endAnimation.setDuration(200);
        endAnimation.setInterpolator(new DecelerateInterpolator());
        endAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                endAnimationEnd();
            }
        });
        endAnimation.start();
    }

    private void endAnimationEnd() {
        Window window = ((Activity) getContext()).getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(statusBarColorPrevious);
        }

        callback.onImageViewLayoutDestroy();
    }

    private void setBackgroundAlpha(float alpha) {
        setBackgroundColor(Color.argb((int) (alpha * 255f), 0, 0, 0));

        if (Build.VERSION.SDK_INT >= 21) {
            Window window = ((Activity) getContext()).getWindow();

            int r = (int) ((1f - alpha) * Color.red(statusBarColorPrevious));
            int g = (int) ((1f - alpha) * Color.green(statusBarColorPrevious));
            int b = (int) ((1f - alpha) * Color.blue(statusBarColorPrevious));

            window.setStatusBarColor(Color.argb(255, r, g, b));
        }
    }

    public interface Callback {
        public Rect getImageViewLayoutStartBounds();

        public void onImageViewLayoutDestroy();
    }
}

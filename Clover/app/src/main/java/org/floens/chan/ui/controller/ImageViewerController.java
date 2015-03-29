package org.floens.chan.ui.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.presenter.ImageViewerPresenter;
import org.floens.chan.ui.adapter.ImageViewerAdapter;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.ui.view.MultiImageView;
import org.floens.chan.ui.view.OptionalSwipeViewPager;
import org.floens.chan.ui.view.TransitionImageView;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ImageViewerController extends Controller implements View.OnClickListener, ImageViewerPresenter.Callback {
    private static final String TAG = "ImageViewerController";
    private static final int TRANSITION_DURATION = 200;
    private static final float TRANSITION_FINAL_ALPHA = 0.80f;

    private int statusBarColorPrevious;
    private AnimatorSet startPreviewAnimation;
    private AnimatorSet endAnimation;

    private PreviewCallback previewCallback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private TransitionImageView previewImage;
    private OptionalSwipeViewPager pager;

    public ImageViewerController(Context context, Toolbar toolbar) {
        super(context);
        this.toolbar = toolbar;

        presenter = new ImageViewerPresenter(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_image_viewer);
        view.setOnClickListener(this);
        previewImage = (TransitionImageView) view.findViewById(R.id.preview_image);
        pager = (OptionalSwipeViewPager) view.findViewById(R.id.pager);
        pager.setOnPageChangeListener(presenter);

        AndroidUtils.waitForMeasure(view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                presenter.onViewMeasured();
                return true;
            }
        });
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

    public void setPreviewCallback(PreviewCallback previewCallback) {
        this.previewCallback = previewCallback;
    }

    public ImageViewerPresenter getPresenter() {
        return presenter;
    }

    public void setPreviewVisibility(boolean visible) {
        previewImage.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public void setPagerVisiblity(boolean visible) {
        pager.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        pager.setSwipingEnabled(visible);
    }

    public void setPagerItems(List<PostImage> images, int initialIndex) {
        ImageViewerAdapter adapter = new ImageViewerAdapter(context, images, presenter);
        pager.setAdapter(adapter);
        pager.setCurrentItem(initialIndex);
    }

    public void setImageMode(PostImage postImage, MultiImageView.Mode mode) {
        ((ImageViewerAdapter) pager.getAdapter()).setMode(postImage, mode);
    }

    public MultiImageView.Mode getImageMode(PostImage postImage) {
        return ((ImageViewerAdapter) pager.getAdapter()).getMode(postImage);
    }

    public void setTitle(PostImage postImage) {
        navigationItem.title = postImage.filename;
        toolbar.setNavigationItem(false, false, navigationItem);
    }

    public void scrollTo(PostImage postImage) {
        previewCallback.scrollTo(postImage);
    }

    public void startPreviewInTransition(PostImage postImage) {
        ImageView startImageView = getTransitionImageView(postImage);

        if (!setTransitionViewData(startImageView)) {
            return; // TODO
        }

        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = getWindow().getStatusBarColor();
        }

        startPreviewAnimation = new AnimatorSet();

        ValueAnimator progress = ValueAnimator.ofFloat(0f, 1f);
        progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setBackgroundAlpha((float) animation.getAnimatedValue());
                previewImage.setProgress((float) animation.getAnimatedValue());
            }
        });

        startPreviewAnimation.play(progress);
        startPreviewAnimation.setDuration(TRANSITION_DURATION);
        startPreviewAnimation.setInterpolator(new DecelerateInterpolator());
        startPreviewAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                previewCallback.onPreviewCreate(ImageViewerController.this);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                startPreviewAnimation = null;
                presenter.onInTransitionEnd();
            }
        });
        startPreviewAnimation.start();
    }

    public void startPreviewOutTransition(final PostImage postImage) {
        if (startPreviewAnimation != null || endAnimation != null) {
            return;
        }

        // Should definitely be loaded
        ChanApplication.getVolleyImageLoader().get(postImage.thumbnailUrl, new ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "onErrorResponse for preview out transition in ImageViewerController, cannot show correct transition bitmap");
                doPreviewOutAnimation(postImage, null);
            }

            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                doPreviewOutAnimation(postImage, response.getBitmap());
            }
        }, previewImage.getWidth(), previewImage.getHeight());
    }

    private void doPreviewOutAnimation(PostImage postImage, Bitmap bitmap) {
        ImageView startImage = getTransitionImageView(postImage);

        if (!setTransitionViewData(startImage) || bitmap == null) {
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
            endAnimation = new AnimatorSet();

            ValueAnimator progress = ValueAnimator.ofFloat(1f, 0f);
            progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setBackgroundAlpha((float) animation.getAnimatedValue());
                    previewImage.setProgress((float) animation.getAnimatedValue());
                }
            });

            endAnimation.play(progress);
            endAnimation.setDuration(TRANSITION_DURATION);
            endAnimation.setInterpolator(new DecelerateInterpolator());
            endAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    previewCallback.onPreviewCreate(ImageViewerController.this);
                }

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

        previewCallback.onPreviewDestroy(this);
        navigationController.stopPresenting(false);
    }

    private boolean setTransitionViewData(ImageView startView) {
        if (startView == null || startView.getWindowToken() == null) {
            return false;
        }

        Bitmap bitmap = ((BitmapDrawable) startView.getDrawable()).getBitmap();
        if (bitmap == null) {
            return false;
        }

        int[] loc = new int[2];
        startView.getLocationInWindow(loc);
        Point windowLocation = new Point(loc[0], loc[1]);
        Point size = new Point(startView.getWidth(), startView.getHeight());
        previewImage.setSourceImageView(windowLocation, size, bitmap);
        return true;
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

    private ImageView getTransitionImageView(PostImage postImage) {
        return previewCallback.getPreviewImageTransitionView(this, postImage);
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface PreviewCallback {
        public ImageView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage);

        public void onPreviewCreate(ImageViewerController imageViewerController);

        public void onPreviewDestroy(ImageViewerController imageViewerController);

        public void scrollTo(PostImage postImage);
    }
}

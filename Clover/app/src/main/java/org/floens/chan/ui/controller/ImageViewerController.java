package org.floens.chan.ui.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.davemorrissey.labs.subscaleview.ImageViewState;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ImageSearch;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.presenter.ImageViewerPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.ImageViewerAdapter;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.CustomScaleImageView;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.LoadingBar;
import org.floens.chan.ui.view.MultiImageView;
import org.floens.chan.ui.view.OptionalSwipeViewPager;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.ui.view.TransitionImageView;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.ImageSaver;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ImageViewerController extends Controller implements View.OnClickListener, ImageViewerPresenter.Callback, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final String TAG = "ImageViewerController";
    private static final int TRANSITION_DURATION = 200;
    private static final float TRANSITION_FINAL_ALPHA = 0.85f;

    private static final int SAVE_ID = 101;
    private static final int OPEN_BROWSER_ID = 102;
    private static final int SHARE_ID = 103;
    private static final int SEARCH_ID = 104;
    private static final int SAVE_ALBUM = 105;

    private int statusBarColorPrevious;
    private AnimatorSet startAnimation;
    private AnimatorSet endAnimation;

    private PreviewCallback previewCallback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private TransitionImageView previewImage;
    private OptionalSwipeViewPager pager;
    private LoadingBar loadingBar;

    private ToolbarMenuItem overflowMenuItem;

    public ImageViewerController(Context context, Toolbar toolbar) {
        super(context);
        this.toolbar = toolbar;

        presenter = new ImageViewerPresenter(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.menu = new ToolbarMenu(context);
        overflowMenuItem = navigationItem.createOverflow(context, this, Arrays.asList(
                new FloatingMenuItem(SAVE_ID, string(R.string.image_save)),
                new FloatingMenuItem(OPEN_BROWSER_ID, string(R.string.action_open_browser)),
                new FloatingMenuItem(SHARE_ID, string(R.string.action_share)),
                new FloatingMenuItem(SEARCH_ID, string(R.string.action_search_image)),
                new FloatingMenuItem(SAVE_ALBUM, string(R.string.action_download_album))
        ));

        view = inflateRes(R.layout.controller_image_viewer);
        view.setOnClickListener(this);
        previewImage = (TransitionImageView) view.findViewById(R.id.preview_image);
        pager = (OptionalSwipeViewPager) view.findViewById(R.id.pager);
        pager.setOnPageChangeListener(presenter);
        loadingBar = (LoadingBar) view.findViewById(R.id.loading_bar);

        AndroidUtils.waitForMeasure(view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                presenter.onViewMeasured();
                return true;
            }
        });
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        switch ((Integer) item.getId()) {
            case SAVE_ID:
            case SHARE_ID:
                if (ChanSettings.shareUrl.get()) {
                    AndroidUtils.shareLink(postImage.imageUrl);
                } else {
                    ImageSaver.getInstance().saveImage(context, postImage.imageUrl,
                            ChanSettings.saveOriginalFilename.get() ? postImage.originalName : postImage.filename,
                            postImage.extension,
                            ((Integer) item.getId()) == SHARE_ID);
                }
                break;
            case OPEN_BROWSER_ID:
                AndroidUtils.openLink(postImage.imageUrl);
                break;
            case SEARCH_ID:
                List<FloatingMenuItem> items = new ArrayList<>();
                for (ImageSearch imageSearch : ImageSearch.engines) {
                    items.add(new FloatingMenuItem(imageSearch.getId(), imageSearch.getName()));
                }
                FloatingMenu menu = new FloatingMenu(context, overflowMenuItem.getView(), items);
                menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
                    @Override
                    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                        for (ImageSearch imageSearch : ImageSearch.engines) {
                            if (((Integer) item.getId()) == imageSearch.getId()) {
                                AndroidUtils.openLink(imageSearch.getUrl(presenter.getCurrentPostImage().imageUrl));
                                break;
                            }
                        }
                    }
                });
                menu.show();
                break;
            case SAVE_ALBUM:
                List<PostImage> all = presenter.getAllPostImages();
                List<ImageSaver.DownloadPair> list = new ArrayList<>();

                String folderName = presenter.getLoadable().title;
                if (TextUtils.isEmpty(folderName)) {
                    folderName = String.valueOf(presenter.getLoadable().no);
                }

                String filename;
                for (PostImage post : all) {
                    filename = (ChanSettings.saveOriginalFilename.get() ? postImage.originalName : postImage.filename) + "." + post.extension;
                    list.add(new ImageSaver.DownloadPair(post.imageUrl, filename));
                }

                ImageSaver.getInstance().saveAll(context, folderName, list);
                break;
        }
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
        navigationItem.updateTitle();
    }

    public void scrollToImage(PostImage postImage) {
        previewCallback.scrollToImage(postImage);
    }

    public void showProgress(boolean show) {
        loadingBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public void onLoadProgress(float progress) {
        loadingBar.setProgress(progress);
    }

    public void onVideoError(MultiImageView multiImageView) {
        if (ChanSettings.videoErrorIgnore.get()) {
            Toast.makeText(context, R.string.image_open_failed, Toast.LENGTH_SHORT).show();
        } else {
            View notice = LayoutInflater.from(context).inflate(R.layout.dialog_video_error, null);
            final CheckBox dontShowAgain = (CheckBox) notice.findViewById(R.id.checkbox);

            new AlertDialog.Builder(context)
                    .setTitle(R.string.video_playback_warning_title)
                    .setView(notice)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (dontShowAgain.isChecked()) {
                                ChanSettings.videoErrorIgnore.set(true);
                            }
                        }
                    })
                    .setCancelable(false)
                    .show();
        }
    }

    public void startPreviewInTransition(PostImage postImage) {
        ThumbnailView startImageView = getTransitionImageView(postImage);

        if (!setTransitionViewData(startImageView)) {
            Logger.test("Oops");
            return; // TODO
        }

        if (Build.VERSION.SDK_INT >= 21) {
            statusBarColorPrevious = getWindow().getStatusBarColor();
        }

        startAnimation = new AnimatorSet();

        ValueAnimator progress = ValueAnimator.ofFloat(0f, 1f);
        progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setBackgroundAlpha((float) animation.getAnimatedValue());
                previewImage.setProgress((float) animation.getAnimatedValue());
            }
        });

        startAnimation.play(progress);
        startAnimation.setDuration(TRANSITION_DURATION);
        startAnimation.setInterpolator(new DecelerateInterpolator());
        startAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                previewCallback.onPreviewCreate(ImageViewerController.this);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                startAnimation = null;
                presenter.onInTransitionEnd();
            }
        });
        startAnimation.start();
    }

    public void startPreviewOutTransition(final PostImage postImage) {
        if (startAnimation != null || endAnimation != null) {
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
        // Find translation and scale if the current displayed image was a bigimage
        MultiImageView multiImageView = ((ImageViewerAdapter) pager.getAdapter()).find(postImage);
        CustomScaleImageView customScaleImageView = multiImageView.findScaleImageView();
        if (customScaleImageView != null) {
            ImageViewState state = customScaleImageView.getState();
            if (state != null) {
                PointF p = customScaleImageView.viewToSourceCoord(0f, 0f);
                PointF bitmapSize = new PointF(customScaleImageView.getSWidth(), customScaleImageView.getSHeight());
                previewImage.setState(state.getScale(), p, bitmapSize);
            }
        }

        ThumbnailView startImage = getTransitionImageView(postImage);

        endAnimation = new AnimatorSet();
        if (!setTransitionViewData(startImage) || bitmap == null) {
            if (bitmap != null) {
                previewImage.setBitmap(bitmap);
            }
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

        } else {
            ValueAnimator progress = ValueAnimator.ofFloat(1f, 0f);
            progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    setBackgroundAlpha((float) animation.getAnimatedValue());
                    previewImage.setProgress((float) animation.getAnimatedValue());
                }
            });

            endAnimation.play(progress);
        }
        endAnimation.setDuration(TRANSITION_DURATION);
        endAnimation.setInterpolator(new DecelerateInterpolator());
        endAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                previewOutAnimationEnded();
            }
        });
        endAnimation.start();
    }

    private void previewOutAnimationEnded() {
        setBackgroundAlpha(0f);

        previewCallback.onPreviewDestroy(this);
        navigationController.stopPresenting(false);
    }

    private boolean setTransitionViewData(ThumbnailView startView) {
        if (startView == null || startView.getWindowToken() == null) {
            return false;
        }

        Bitmap bitmap = startView.getBitmap();
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

    private ThumbnailView getTransitionImageView(PostImage postImage) {
        return previewCallback.getPreviewImageTransitionView(this, postImage);
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface PreviewCallback {
        ThumbnailView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage);

        void onPreviewCreate(ImageViewerController imageViewerController);

        void onPreviewDestroy(ImageViewerController imageViewerController);

        void scrollToImage(PostImage postImage);
    }
}

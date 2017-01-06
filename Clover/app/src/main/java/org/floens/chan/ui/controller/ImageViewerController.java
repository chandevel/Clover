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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.Toast;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.davemorrissey.labs.subscaleview.ImageViewState;

import org.floens.chan.R;
import org.floens.chan.chan.ImageSearch;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.presenter.ImageViewerPresenter;
import org.floens.chan.core.saver.ImageSaveTask;
import org.floens.chan.core.saver.ImageSaver;
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
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getString;

public class ImageViewerController extends Controller implements ImageViewerPresenter.Callback, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final String TAG = "ImageViewerController";
    private static final int TRANSITION_DURATION = 300;
    private static final float TRANSITION_FINAL_ALPHA = 0.85f;

    private static final int GO_POST_ID = 1;
    private static final int SAVE_ID = 2;
    private static final int OPEN_BROWSER_ID = 103;
    private static final int SHARE_ID = 104;
    private static final int SEARCH_ID = 105;
    private static final int SAVE_ALBUM = 106;

    @Inject
    ImageLoader imageLoader;

    private int statusBarColorPrevious;
    private AnimatorSet startAnimation;
    private AnimatorSet endAnimation;

    private ImageViewerCallback imageViewerCallback;
    private GoPostCallback goPostCallback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private TransitionImageView previewImage;
    private OptionalSwipeViewPager pager;
    private LoadingBar loadingBar;

    private ToolbarMenuItem overflowMenuItem;

    public ImageViewerController(Context context, Toolbar toolbar) {
        super(context);
        getGraph().inject(this);

        this.toolbar = toolbar;

        presenter = new ImageViewerPresenter(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        navigationItem.subtitle = "0";
        navigationItem.menu = new ToolbarMenu(context);
        if (goPostCallback != null) {
            navigationItem.menu.addItem(new ToolbarMenuItem(context, this, GO_POST_ID, R.drawable.ic_subdirectory_arrow_left_white_24dp));
        }
        navigationItem.menu.addItem(new ToolbarMenuItem(context, this, SAVE_ID, R.drawable.ic_file_download_white_24dp));

        List<FloatingMenuItem> items = new ArrayList<>();
        items.add(new FloatingMenuItem(OPEN_BROWSER_ID, R.string.action_open_browser));
        items.add(new FloatingMenuItem(SHARE_ID, R.string.action_share));
        items.add(new FloatingMenuItem(SEARCH_ID, R.string.action_search_image));
        items.add(new FloatingMenuItem(SAVE_ALBUM, R.string.action_download_album));
        overflowMenuItem = navigationItem.createOverflow(context, this, items);

        view = inflateRes(R.layout.controller_image_viewer);
        previewImage = (TransitionImageView) view.findViewById(R.id.preview_image);
        pager = (OptionalSwipeViewPager) view.findViewById(R.id.pager);
        pager.addOnPageChangeListener(presenter);
        loadingBar = (LoadingBar) view.findViewById(R.id.loading_bar);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        AndroidUtils.waitForLayout(parentController.view.getViewTreeObserver(), view, new AndroidUtils.OnMeasuredCallback() {
            @Override
            public boolean onMeasured(View view) {
                presenter.onViewMeasured();
                return true;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch ((Integer) item.getId()) {
            case GO_POST_ID:
                PostImage postImage = presenter.getCurrentPostImage();
                ImageViewerCallback imageViewerCallback = goPostCallback.goToPost(postImage);
                if (imageViewerCallback != null) {
                    // hax: we need to wait for the recyclerview to do a layout before we know
                    // where the new thumbnails are to get the bounds from to animate to
                    this.imageViewerCallback = imageViewerCallback;
                    AndroidUtils.waitForLayout(view, new AndroidUtils.OnMeasuredCallback() {
                        @Override
                        public boolean onMeasured(View view) {
                            presenter.onExit();
                            return false;
                        }
                    });
                } else {
                    presenter.onExit();
                }
                break;
            case SAVE_ID:
                saveShare(false, presenter.getCurrentPostImage());
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        switch ((Integer) item.getId()) {
            case SHARE_ID:
                saveShare(true, postImage);
                break;
            case OPEN_BROWSER_ID:
                AndroidUtils.openLinkInBrowser((Activity) context, postImage.imageUrl.toString());
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
                                AndroidUtils.openLinkInBrowser((Activity) context, imageSearch.getUrl(presenter.getCurrentPostImage().imageUrl.toString()));
                                break;
                            }
                        }
                    }

                    @Override
                    public void onFloatingMenuDismissed(FloatingMenu menu) {
                    }
                });
                menu.show();
                break;
            case SAVE_ALBUM:
                List<PostImage> all = presenter.getAllPostImages();
                AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
                albumDownloadController.setPostImages(presenter.getLoadable(), all);
                navigationController.pushController(albumDownloadController);
                break;
        }
    }

    private void saveShare(boolean share, PostImage postImage) {
        if (share && ChanSettings.shareUrl.get()) {
            AndroidUtils.shareLink(postImage.imageUrl.toString());
        } else {
            ImageSaveTask task = new ImageSaveTask(postImage);
            task.setShare(share);
            if (ChanSettings.saveBoardFolder.get()) {
                task.setSubFolder(presenter.getLoadable().boardCode);
            }
            ImageSaver.getInstance().startDownloadTask(context, task);
        }
    }

    @Override
    public boolean onBack() {
        presenter.onExit();
        return true;
    }

    public void setImageViewerCallback(ImageViewerCallback imageViewerCallback) {
        this.imageViewerCallback = imageViewerCallback;
    }

    public void setGoPostCallback(GoPostCallback goPostCallback) {
        this.goPostCallback = goPostCallback;
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

    public void setTitle(PostImage postImage, int index, int count, boolean spoiler) {
        if (spoiler) {
            navigationItem.title = getString(R.string.image_spoiler_filename);
        } else {
            navigationItem.title = postImage.filename + "." + postImage.extension;
        }
        navigationItem.subtitle = (index + 1) + "/" + count;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigationItem);
    }

    public void scrollToImage(PostImage postImage) {
        imageViewerCallback.scrollToImage(postImage);
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
            @SuppressLint("InflateParams")
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

        setBackgroundAlpha(0f);

        startAnimation = new AnimatorSet();

        ValueAnimator progress = ValueAnimator.ofFloat(0f, 1f);
        progress.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setBackgroundAlpha(Math.min(1f, (float) animation.getAnimatedValue()));
                previewImage.setProgress((float) animation.getAnimatedValue());
            }
        });

        startAnimation.play(progress);
        startAnimation.setDuration(TRANSITION_DURATION);
        startAnimation.setInterpolator(new DecelerateInterpolator(3f));
        startAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                imageViewerCallback.onPreviewCreate(ImageViewerController.this);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                startAnimation = null;
                presenter.onInTransitionEnd();
            }
        });

        imageLoader.get(postImage.thumbnailUrl.toString(), new ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "onErrorResponse for preview in transition in ImageViewerController, cannot show correct transition bitmap");
                startAnimation.start();
            }

            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null) {
                    previewImage.setBitmap(response.getBitmap());
                    startAnimation.start();
                }
            }
        }, previewImage.getWidth(), previewImage.getHeight());
    }

    public void startPreviewOutTransition(final PostImage postImage) {
        if (startAnimation != null || endAnimation != null) {
            return;
        }

        imageLoader.get(postImage.thumbnailUrl.toString(), new ImageLoader.ImageListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "onErrorResponse for preview out transition in ImageViewerController, cannot show correct transition bitmap");
                doPreviewOutAnimation(postImage, null);
            }

            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                if (response.getBitmap() != null) {
                    doPreviewOutAnimation(postImage, response.getBitmap());
                }
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
        endAnimation.setInterpolator(new DecelerateInterpolator(3f));
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

        imageViewerCallback.onPreviewDestroy(this);
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
        navigationController.view.setBackgroundColor(Color.argb((int) (alpha * TRANSITION_FINAL_ALPHA * 255f), 0, 0, 0));

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
        loadingBar.setAlpha(alpha);
    }

    private ThumbnailView getTransitionImageView(PostImage postImage) {
        return imageViewerCallback.getPreviewImageTransitionView(this, postImage);
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface ImageViewerCallback {
        ThumbnailView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage);

        void onPreviewCreate(ImageViewerController imageViewerController);

        void onPreviewDestroy(ImageViewerController imageViewerController);

        void scrollToImage(PostImage postImage);
    }

    public interface GoPostCallback {
        ImageViewerCallback goToPost(PostImage postImage);
    }
}

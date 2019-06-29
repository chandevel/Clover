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
import android.graphics.PointF;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ImageViewerPresenter;
import com.github.adamantcheese.chan.core.saver.ImageSaveTask;
import com.github.adamantcheese.chan.core.saver.ImageSaver;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.ImageSearch;
import com.github.adamantcheese.chan.ui.adapter.ImageViewerAdapter;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenu;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.ui.view.CustomScaleImageView;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.LoadingBar;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.ui.view.OptionalSwipeViewPager;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.ui.view.TransitionImageView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ImageViewerController extends Controller implements ImageViewerPresenter.Callback {
    private static final String TAG = "ImageViewerController";
    private static final int TRANSITION_DURATION = 300;
    private static final float TRANSITION_FINAL_ALPHA = 0.85f;

    private static final int VOLUME_ID = 1;
    private static final int SAVE_ID = 2;

    @Inject
    ImageLoader imageLoader;

    private int statusBarColorPrevious;
    private AnimatorSet startAnimation;
    private AnimatorSet endAnimation;

    private ImageViewerCallback imageViewerCallback;
    private GoPostCallback goPostCallback;
    private ImageViewerPresenter presenter;

    private final Toolbar toolbar;
    private Loadable loadable;
    private TransitionImageView previewImage;
    private OptionalSwipeViewPager pager;
    private LoadingBar loadingBar;

    public ImageViewerController(Loadable loadable, Context context, Toolbar toolbar) {
        super(context);
        inject(this);

        this.toolbar = toolbar;
        this.loadable = loadable;

        presenter = new ImageViewerPresenter(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Navigation
        navigation.subtitle = "0";

        NavigationItem.MenuBuilder menuBuilder = navigation.buildMenu();
        if (goPostCallback != null) {
            menuBuilder.withItem(R.drawable.ic_subdirectory_arrow_left_white_24dp, this::goPostClicked);
        }

        menuBuilder.withItem(VOLUME_ID, R.drawable.ic_volume_off_white_24dp, this::volumeClicked);

        if (!loadable.isSavedCopy) {
            menuBuilder.withItem(SAVE_ID, R.drawable.ic_file_download_white_24dp, this::saveClicked);
        }

        NavigationItem.MenuOverflowBuilder overflowBuilder = menuBuilder.withOverflow();
        overflowBuilder.withSubItem(R.string.action_open_browser, this::openBrowserClicked);
        if (!loadable.isSavedCopy) {
            overflowBuilder.withSubItem(R.string.action_share, this::shareClicked);
        }
        overflowBuilder.withSubItem(R.string.action_search_image, this::searchClicked);
        overflowBuilder.withSubItem(R.string.action_download_album, this::downloadAlbumClicked);
        overflowBuilder.withSubItem(R.string.action_transparency_toggle, this::toggleTransparency);
        overflowBuilder.withSubItem(R.string.action_image_rotate_cw, this::rotateImageCW);
        overflowBuilder.withSubItem(R.string.action_image_rotate_ccw, this::rotateImageCCW);

        overflowBuilder.build().build();

        // View setup
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = inflateRes(R.layout.controller_image_viewer);
        previewImage = view.findViewById(R.id.preview_image);
        pager = view.findViewById(R.id.pager);
        pager.addOnPageChangeListener(presenter);
        loadingBar = view.findViewById(R.id.loading_bar);

        showVolumeMenuItem(false, true);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        AndroidUtils.waitForLayout(parentController.view.getViewTreeObserver(), view, view -> {
            presenter.onViewMeasured();
            return true;
        });
    }

    private void goPostClicked(ToolbarMenuItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        ImageViewerCallback imageViewerCallback = goPostCallback.goToPost(postImage);
        if (imageViewerCallback != null) {
            // hax: we need to wait for the recyclerview to do a layout before we know
            // where the new thumbnails are to get the bounds from to animate to
            this.imageViewerCallback = imageViewerCallback;
            AndroidUtils.waitForLayout(view, view -> {
                presenter.onExit();
                return false;
            });
        } else {
            presenter.onExit();
        }
    }

    private void volumeClicked(ToolbarMenuItem item) {
        presenter.onVolumeClicked();
    }

    private void saveClicked(ToolbarMenuItem item) {
        item.setCallback(null);
        item.setImage(R.drawable.ic_file_download_grey_24dp);
        saveShare(false, presenter.getCurrentPostImage());
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        if (ChanSettings.openLinkBrowser.get()) {
            AndroidUtils.openLink(postImage.imageUrl.toString());
        } else {
            AndroidUtils.openLinkInBrowser((Activity) context, postImage.imageUrl.toString());
        }
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        saveShare(true, postImage);
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        showImageSearchOptions();
    }

    private void downloadAlbumClicked(ToolbarMenuSubItem item) {
        List<PostImage> all = presenter.getAllPostImages();
        AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
        albumDownloadController.setPostImages(presenter.getLoadable(), all);
        navigationController.pushController(albumDownloadController);
    }

    private void toggleTransparency(ToolbarMenuSubItem item) {
        ((ImageViewerAdapter) pager.getAdapter()).toggleTransparency(presenter.getCurrentPostImage());
    }

    private void rotateImageCW(ToolbarMenuSubItem item) {
        ((ImageViewerAdapter) pager.getAdapter()).rotateImage(presenter.getCurrentPostImage(), true);
    }

    private void rotateImageCCW(ToolbarMenuSubItem item) {
        ((ImageViewerAdapter) pager.getAdapter()).rotateImage(presenter.getCurrentPostImage(), false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void saveShare(boolean share, PostImage postImage) {
        if (share && ChanSettings.shareUrl.get()) {
            AndroidUtils.shareLink(postImage.imageUrl.toString());
        } else {
            ImageSaveTask task = new ImageSaveTask(loadable, postImage);
            task.setShare(share);
            if (ChanSettings.saveBoardFolder.get()) {
                String subFolderName =
                        presenter.getLoadable().site.name() +
                                File.separator +
                                presenter.getLoadable().boardCode;
                if (ChanSettings.saveThreadFolder.get()) {
                    //save to op no appended with the first 50 characters of the subject
                    //should be unique and perfectly understandable title wise
                    subFolderName = subFolderName +
                            File.separator +
                            presenter.getLoadable().no +
                            "_";
                    String tempTitle = presenter.getLoadable().title
                            .toLowerCase()
                            .replaceAll(" ", "_")
                            .replaceAll("[^a-z0-9_]", "");
                    tempTitle = tempTitle.substring(0, Math.min(tempTitle.length(), 50));
                    subFolderName = subFolderName + tempTitle;
                }
                task.setSubFolder(subFolderName);
            }
            Chan.injector().instance(ImageSaver.class).startDownloadTask(context, task);
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

    public void setPagerItems(Loadable loadable, List<PostImage> images, int initialIndex) {
        ImageViewerAdapter adapter = new ImageViewerAdapter(context, images, loadable, presenter);
        pager.setAdapter(adapter);
        pager.setCurrentItem(initialIndex);
    }

    public void setImageMode(PostImage postImage, MultiImageView.Mode mode, boolean center) {
        ((ImageViewerAdapter) pager.getAdapter()).setMode(postImage, mode, center);
    }

    @Override
    public void setVolume(PostImage postImage, boolean muted) {
        ((ImageViewerAdapter) pager.getAdapter()).setVolume(postImage, muted);
    }

    public MultiImageView.Mode getImageMode(PostImage postImage) {
        return ((ImageViewerAdapter) pager.getAdapter()).getMode(postImage);
    }

    public void setTitle(PostImage postImage, int index, int count, boolean spoiler) {
        if (spoiler) {
            navigation.title = getString(R.string.image_spoiler_filename);
        } else {
            navigation.title = postImage.filename + "." + postImage.extension;
        }
        navigation.subtitle = (index + 1) + "/" + count;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
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

    @Override
    public void showVolumeMenuItem(boolean show, boolean muted) {
        ToolbarMenuItem volumeMenuItem = navigation.findItem(VOLUME_ID);
        volumeMenuItem.setVisible(show);
        volumeMenuItem.setImage(
                muted ? R.drawable.ic_volume_off_white_24dp : R.drawable.ic_volume_up_white_24dp);
    }

    @Override
    public void resetDownloadButtonState() {
        navigation.findItem(SAVE_ID).setImage(R.drawable.ic_file_download_white_24dp);
        navigation.findItem(SAVE_ID).setCallback(this::saveClicked);
    }

    private void showImageSearchOptions() {
        // TODO: move to presenter
        List<FloatingMenuItem> items = new ArrayList<>();
        for (ImageSearch imageSearch : ImageSearch.engines) {
            items.add(new FloatingMenuItem(imageSearch.getId(), imageSearch.getName()));
        }
        ToolbarMenuItem overflowMenuItem = navigation.findItem(ToolbarMenu.OVERFLOW_ID);
        FloatingMenu menu = new FloatingMenu(context, overflowMenuItem.getView(), items);
        menu.setCallback(new FloatingMenu.FloatingMenuCallback() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
                for (ImageSearch imageSearch : ImageSearch.engines) {
                    if (((Integer) item.getId()) == imageSearch.getId()) {
                        final HttpUrl searchImageUrl = getSearchImageUrl(presenter.getCurrentPostImage());
                        AndroidUtils.openLinkInBrowser((Activity) context, imageSearch.getUrl(searchImageUrl.toString()));
                        break;
                    }
                }
            }

            @Override
            public void onFloatingMenuDismissed(FloatingMenu menu) {
            }
        });
        menu.show();
    }

    public void startPreviewInTransition(Loadable loadable, PostImage postImage) {
        ThumbnailView startImageView = getTransitionImageView(postImage);

        if (!setTransitionViewData(startImageView)) {
            Logger.test("Oops");
            return; // TODO
        }

        statusBarColorPrevious = getWindow().getStatusBarColor();

        setBackgroundAlpha(0f);

        startAnimation = new AnimatorSet();

        ValueAnimator progress = ValueAnimator.ofFloat(0f, 1f);
        progress.addUpdateListener(animation -> {
            setBackgroundAlpha(Math.min(1f, (float) animation.getAnimatedValue()));
            previewImage.setProgress((float) animation.getAnimatedValue());
        });

        startAnimation.play(progress);
        startAnimation.setDuration(TRANSITION_DURATION);
        startAnimation.setInterpolator(new DecelerateInterpolator(3f));
        startAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                startAnimation = null;
                presenter.onInTransitionEnd();
            }
        });

        imageLoader.getImage(
                loadable,
                postImage,
                previewImage.getWidth(),
                previewImage.getHeight(),
                new ImageLoader.ImageListener() {
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
                });
    }

    public void startPreviewOutTransition(Loadable loadable, final PostImage postImage) {
        if (startAnimation != null || endAnimation != null) {
            return;
        }


        imageLoader.getImage(
                loadable,
                postImage,
                previewImage.getWidth(),
                previewImage.getHeight(),
                new ImageLoader.ImageListener() {
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
                }
        );
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
            backgroundAlpha.addUpdateListener(animation -> setBackgroundAlpha((float) animation.getAnimatedValue()));

            endAnimation
                    .play(ObjectAnimator.ofFloat(previewImage, View.Y, previewImage.getTop(), previewImage.getTop() + dp(20)))
                    .with(ObjectAnimator.ofFloat(previewImage, View.ALPHA, 1f, 0f))
                    .with(backgroundAlpha);

        } else {
            ValueAnimator progress = ValueAnimator.ofFloat(1f, 0f);
            progress.addUpdateListener(animation -> {
                setBackgroundAlpha((float) animation.getAnimatedValue());
                previewImage.setProgress((float) animation.getAnimatedValue());
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

        if (alpha == 0f) {
            getWindow().setStatusBarColor(statusBarColorPrevious);
        } else {
            int r = (int) ((1f - alpha) * Color.red(statusBarColorPrevious));
            int g = (int) ((1f - alpha) * Color.green(statusBarColorPrevious));
            int b = (int) ((1f - alpha) * Color.blue(statusBarColorPrevious));
            getWindow().setStatusBarColor(Color.argb(255, r, g, b));
        }

        toolbar.setAlpha(alpha);
        loadingBar.setAlpha(alpha);
    }

    private ThumbnailView getTransitionImageView(PostImage postImage) {
        return imageViewerCallback.getPreviewImageTransitionView(postImage);
    }

    private Window getWindow() {
        return ((Activity) context).getWindow();
    }

    public interface ImageViewerCallback {
        ThumbnailView getPreviewImageTransitionView(PostImage postImage);

        void scrollToImage(PostImage postImage);
    }

    public interface GoPostCallback {
        ImageViewerCallback goToPost(PostImage postImage);
    }

    /**
     * Send thumbnail image of movie posts because none of the image search providers support movies (such as webm) directly
     *
     * @param postImage the post image
     * @return url of an image to be searched
     */
    private HttpUrl getSearchImageUrl(final PostImage postImage) {
        return postImage.type == PostImage.Type.MOVIE ? postImage.thumbnailUrl : postImage.imageUrl;
    }
}

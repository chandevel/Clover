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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.image.ImageLoaderV2;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ImageViewerPresenter;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.saver.ImageSaveTask;
import com.github.adamantcheese.chan.core.saver.ImageSaver;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.adapter.ImageViewerAdapter;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.NavigationItem;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.ui.view.CustomScaleImageView;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.LoadingBar;
import com.github.adamantcheese.chan.ui.view.MultiImageView;
import com.github.adamantcheese.chan.ui.view.OptionalSwipeViewPager;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.ui.view.TransitionImageView;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getDimen;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getWindow;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.shareLink;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.waitForLayout;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class ImageViewerController
        extends Controller
        implements ImageViewerPresenter.Callback, ToolbarMenuItem.OverflowMenuCallback {
    private static final int TRANSITION_DURATION = 300;
    private static final float TRANSITION_FINAL_ALPHA = 0.85f;

    private static final int VOLUME_ID = 1;
    private static final int SAVE_ID = 2;

    @Inject
    ImageSaver imageSaver;

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

    private boolean isInImmersiveMode = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable uiHideCall = this::hideSystemUI;

    public ImageViewerController(Loadable loadable, Context context, Toolbar toolbar) {
        super(context);
        inject(this);

        this.toolbar = toolbar;
        this.loadable = loadable;

        presenter = new ImageViewerPresenter(context, this);
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
        menuBuilder.withItem(SAVE_ID, R.drawable.ic_file_download_white_24dp, this::saveClicked);

        NavigationItem.MenuOverflowBuilder overflowBuilder = menuBuilder.withOverflow(this);
        overflowBuilder.withSubItem(R.string.action_open_browser, this::openBrowserClicked);
        if (!loadable.isLocal()) {
            overflowBuilder.withSubItem(R.string.action_share, this::shareClicked);
        }
        overflowBuilder.withSubItem(R.string.action_search_image, this::searchClicked);
        if (!loadable.isLocal()) {
            overflowBuilder.withSubItem(R.string.action_download_album, this::downloadAlbumClicked);
        }
        overflowBuilder.withSubItem(R.string.action_transparency_toggle, this::toggleTransparency);

        if (!loadable.isLocal()) {
            overflowBuilder.withSubItem(R.string.action_reload, this::forceReload);
        }

        overflowBuilder.build().build();

        hideSystemUI();

        // View setup
        getWindow(context).addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        view = inflate(context, R.layout.controller_image_viewer);
        previewImage = view.findViewById(R.id.preview_image);
        pager = view.findViewById(R.id.pager);
        pager.addOnPageChangeListener(presenter);
        loadingBar = view.findViewById(R.id.loading_bar);

        showVolumeMenuItem(false, true);

        // Sanity check
        if (parentController.view.getWindowToken() == null) {
            throw new IllegalArgumentException("parentController.view not attached");
        }

        waitForLayout(parentController.view.getViewTreeObserver(), view, view -> {
            ToolbarMenuItem saveMenuItem = navigation.findItem(SAVE_ID);
            if (saveMenuItem != null) {
                saveMenuItem.setEnabled(false);
            }

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
            waitForLayout(view, view -> {
                showSystemUI();
                mainHandler.removeCallbacks(uiHideCall);
                presenter.onExit();
                return false;
            });
        } else {
            showSystemUI();
            mainHandler.removeCallbacks(uiHideCall);
            presenter.onExit();
        }
    }

    private void volumeClicked(ToolbarMenuItem item) {
        presenter.onVolumeClicked();
    }

    private void saveClicked(ToolbarMenuItem item) {
        item.setEnabled(false);
        saveShare(false, presenter.getCurrentPostImage());

        ((ImageViewerAdapter) pager.getAdapter()).onImageSaved(presenter.getCurrentPostImage());
    }

    private void openBrowserClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        if (postImage.imageUrl == null) {
            Logger.e(this, "openBrowserClicked() postImage.imageUrl is null");
            return;
        }

        if (ChanSettings.openLinkBrowser.get()) {
            openLink(postImage.imageUrl.toString());
        } else {
            openLinkInBrowser(context, postImage.imageUrl.toString());
        }
    }

    private void shareClicked(ToolbarMenuSubItem item) {
        PostImage postImage = presenter.getCurrentPostImage();
        saveShare(true, postImage);
    }

    private void searchClicked(ToolbarMenuSubItem item) {
        presenter.showImageSearchOptions(navigation);
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

    private void forceReload(ToolbarMenuSubItem item) {
        ToolbarMenuItem menuItem = navigation.findItem(SAVE_ID);
        if (menuItem != null && presenter.forceReload()) {
            menuItem.setEnabled(false);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        showSystemUI();
        mainHandler.removeCallbacks(uiHideCall);
        getWindow(context).clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void saveShare(boolean share, PostImage postImage) {
        if (share && ChanSettings.shareUrl.get()) {
            if (postImage.imageUrl == null) {
                Logger.e(this, "saveShare() postImage.imageUrl == null");
                return;
            }

            shareLink(postImage.imageUrl.toString());
        } else {
            ImageSaveTask task = new ImageSaveTask(loadable, postImage, share);
            if (ChanSettings.saveImageBoardFolder.get()) {
                String subFolderName;

                if (ChanSettings.saveImageThreadFolder.get()) {
                    subFolderName = appendAdditionalSubDirectories(postImage);
                } else {
                    String siteNameSafe = StringUtils.dirNameRemoveBadCharacters(presenter.getLoadable().site.name());

                    subFolderName = siteNameSafe + File.separator + presenter.getLoadable().boardCode;
                }

                task.setSubFolder(subFolderName);
            }

            imageSaver.startDownloadTask(context, task, message -> {
                String errorMessage = String.format(Locale.ENGLISH,
                        "%s, error message = %s",
                        "Couldn't start download task",
                        message
                );

                showToast(context, errorMessage, Toast.LENGTH_LONG);
            });
        }
    }

    @NonNull
    private String appendAdditionalSubDirectories(PostImage postImage) {
        // save to op no appended with the first 50 characters of the subject
        // should be unique and perfectly understandable title wise
        //
        // if we're saving from the catalog, find the post for the image and use its attributes
        // to keep everything consistent as the loadable is for the catalog and doesn't have
        // the right info

        String siteName = presenter.getLoadable().site.name();

        int postNoString = presenter.getLoadable().no == 0
                ? imageViewerCallback.getPostForPostImage(postImage).no
                : presenter.getLoadable().no;

        String sanitizedSubFolderName = StringUtils.dirNameRemoveBadCharacters(siteName) + File.separator
                + StringUtils.dirNameRemoveBadCharacters(presenter.getLoadable().boardCode) + File.separator
                + postNoString + "_";

        String tempTitle = (presenter.getLoadable().no == 0
                ? PostHelper.getTitle(imageViewerCallback.getPostForPostImage(postImage),
                imageViewerCallback.getLoadable()
        )
                : presenter.getLoadable().title);

        String sanitizedFileName = StringUtils.dirNameRemoveBadCharacters(tempTitle);
        String truncatedFileName = sanitizedFileName.substring(0, Math.min(sanitizedFileName.length(), 50));

        return sanitizedSubFolderName + truncatedFileName;
    }

    @Override
    public boolean onBack() {
        if (presenter.isTransitioning()) return false;
        showSystemUI();
        mainHandler.removeCallbacks(uiHideCall);
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
        previewImage.setVisibility(visible ? VISIBLE : INVISIBLE);
    }

    public void setPagerVisiblity(boolean visible) {
        pager.setVisibility(visible ? VISIBLE : INVISIBLE);
        pager.setSwipingEnabled(visible);
    }

    public void setPagerItems(Loadable loadable, List<PostImage> images, int initialIndex) {
        ImageViewerAdapter adapter = new ImageViewerAdapter(images, loadable, presenter);
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
            navigation.title =
                    getString(R.string.image_spoiler_filename) + " (" + postImage.extension.toUpperCase() + ")";
        } else {
            navigation.title = postImage.filename + "." + postImage.extension;
        }
        navigation.subtitle = (index + 1) + "/" + count;
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
    }

    public void scrollToImage(PostImage postImage) {
        imageViewerCallback.scrollToImage(postImage);
    }

    @Override
    public void updatePreviewImage(PostImage postImage) {
        ImageLoaderV2.getImage(loadable,
                postImage,
                previewImage.getWidth(),
                previewImage.getHeight(),
                new NetUtils.BitmapResult() {
                    @Override
                    public void onBitmapFailure(Bitmap errormap, Exception e) {
                        // the preview image will just remain as the last successful response; good enough
                    }

                    @Override
                    public void onBitmapSuccess(@NonNull Bitmap bitmap, boolean fromCache) {
                        previewImage.setBitmap(bitmap);
                    }
                }
        );
    }

    public void saveImage() {
        ToolbarMenuItem saveMenuItem = navigation.findItem(SAVE_ID);
        if (saveMenuItem != null) {
            saveMenuItem.setEnabled(false);
        }

        saveShare(false, presenter.getCurrentPostImage());
    }

    public void showProgress(boolean show) {
        int visibility = loadingBar.getVisibility();
        if ((visibility == VISIBLE && show) || (visibility == GONE && !show)) {
            return;
        }

        loadingBar.setVisibility(show ? VISIBLE : GONE);
    }

    public void onLoadProgress(Float[] progress) {
        loadingBar.setProgress(progress);
    }

    @Override
    public void showVolumeMenuItem(boolean show, boolean muted) {
        ToolbarMenuItem volumeMenuItem = navigation.findItem(VOLUME_ID);
        volumeMenuItem.setVisible(show);
        volumeMenuItem.setImage(muted ? R.drawable.ic_volume_off_white_24dp : R.drawable.ic_volume_up_white_24dp);
    }

    @Override
    public void showDownloadMenuItem(boolean show) {
        ToolbarMenuItem saveItem = navigation.findItem(SAVE_ID);
        if (saveItem == null) {
            return;
        }

        saveItem.setEnabled(show);
    }

    @Override
    public void onMenuShown(FloatingMenu<ToolbarMenuSubItem> menu) {
        showSystemUI();
    }

    @Override
    public void onMenuHidden() {
        hideSystemUI();
    }

    @Override
    public boolean isImmersive() {
        return ChanSettings.useImmersiveModeForGallery.get() && isInImmersiveMode;
    }

    public void startPreviewInTransition(Loadable loadable, PostImage postImage) {
        ThumbnailView startImageView = getTransitionImageView(postImage);

        statusBarColorPrevious = getWindow(context).getStatusBarColor();

        if (!setTransitionViewData(startImageView)) {
            // Some weird stuff happened, like the user pressed back in the thread, tapped an image, then immediately tapped back again;
            // This should cause this controller to disappear, but if there's no transition image, no transition occurs and we'd
            // get stuck. Instead, just trigger the presenter to be "transitioned", which it can then be backed out of without
            // being stuck in a softlocked controller.
            presenter.onInTransitionEnd();
            return;
        }

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

        ImageLoaderV2.getImage(loadable,
                postImage,
                previewImage.getWidth(),
                previewImage.getHeight(),
                new NetUtils.BitmapResult() {
                    @Override
                    public void onBitmapFailure(Bitmap errormap, Exception e) {
                        Logger.e(
                                ImageViewerController.this,
                                "onBitmapFailure for preview in transition, cannot show correct transition bitmap",
                                e
                        );
                        previewImage.setBitmap(errormap);
                        startAnimation.start();
                    }

                    @Override
                    public void onBitmapSuccess(@NonNull Bitmap bitmap, boolean fromCache) {
                        previewImage.setBitmap(bitmap);
                        startAnimation.start();
                    }
                }
        );
    }

    public void startPreviewOutTransition(Loadable loadable, final PostImage postImage) {
        if (startAnimation != null || endAnimation != null) {
            return;
        }

        doPreviewOutAnimation(postImage);
    }

    private void doPreviewOutAnimation(PostImage postImage) {
        // Find translation and scale if the current displayed image was a bigimage
        MultiImageView multiImageView = ((ImageViewerAdapter) pager.getAdapter()).find(postImage);
        View activeView = multiImageView.getActiveView();
        if (activeView instanceof CustomScaleImageView) {
            CustomScaleImageView scaleImageView = (CustomScaleImageView) activeView;
            ImageViewState state = scaleImageView.getState();
            if (state != null) {
                PointF p = scaleImageView.viewToSourceCoord(0f, 0f);
                PointF bitmapSize = new PointF(scaleImageView.getSWidth(), scaleImageView.getSHeight());
                previewImage.setState(state.getScale(), p, bitmapSize);
            }
        }

        ThumbnailView startImage = getTransitionImageView(postImage);

        endAnimation = new AnimatorSet();
        if (!setTransitionViewData(startImage)) {
            ValueAnimator backgroundAlpha = ValueAnimator.ofFloat(1f, 0f);
            backgroundAlpha.addUpdateListener(animation -> setBackgroundAlpha((float) animation.getAnimatedValue()));

            endAnimation.play(ObjectAnimator.ofFloat(previewImage,
                    View.Y,
                    previewImage.getTop(),
                    previewImage.getTop() + dp(20)
            ))
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
            bitmap = BitmapRepository.error;
        }

        int[] loc = new int[2];
        startView.getLocationInWindow(loc);
        Point windowLocation = new Point(loc[0], loc[1]);
        Point size = new Point(startView.getWidth(), startView.getHeight());
        previewImage.setSourceImageView(windowLocation, size, bitmap);
        return true;
    }

    private void setBackgroundAlpha(float alpha) {
        navigationController.view.setBackgroundColor(Color.argb((int) (alpha * TRANSITION_FINAL_ALPHA * 255f),
                0,
                0,
                0
        ));

        int currentThemePrimaryColor =
                getAttrColor(ThemeHelper.getTheme().primaryColor.primaryColorStyleId, R.attr.colorPrimary);
        toolbar.setBackgroundColor(ColorUtils.blendARGB(currentThemePrimaryColor, Color.BLACK, alpha));
        if (alpha == 0f) {
            getWindow(context).setStatusBarColor(statusBarColorPrevious);
        } else {
            int r = (int) ((1f - alpha) * Color.red(statusBarColorPrevious));
            int g = (int) ((1f - alpha) * Color.green(statusBarColorPrevious));
            int b = (int) ((1f - alpha) * Color.blue(statusBarColorPrevious));
            getWindow(context).setStatusBarColor(Color.argb(255, r, g, b));
        }

        toolbar.setAlpha(alpha);
        loadingBar.setAlpha(alpha);
    }

    private ThumbnailView getTransitionImageView(PostImage postImage) {
        return imageViewerCallback.getPreviewImageTransitionView(postImage);
    }

    private void hideSystemUI() {
        if (!ChanSettings.useImmersiveModeForGallery.get() || isInImmersiveMode) {
            return;
        }

        isInImmersiveMode = true;

        View decorView = getWindow(context).getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);

        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0 && isInImmersiveMode) {
                showSystemUI();
                mainHandler.postDelayed(uiHideCall, 2500);
            }
        });

        //setting this to 0 because GONE doesn't seem to work?
        ViewGroup.LayoutParams params = navigationController.getToolbar().getLayoutParams();
        params.height = 0;
        navigationController.getToolbar().setLayoutParams(params);
    }

    @Override
    public void showSystemUI(boolean show) {
        if (show) {
            showSystemUI();
            mainHandler.postDelayed(uiHideCall, 2500);
        } else {
            hideSystemUI();
        }
    }

    private void showSystemUI() {
        if (!ChanSettings.useImmersiveModeForGallery.get() || !isInImmersiveMode) {
            return;
        }

        isInImmersiveMode = false;

        View decorView = getWindow(context).getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(null);
        decorView.setSystemUiVisibility(0);

        //setting this to the toolbar height because VISIBLE doesn't seem to work?
        ViewGroup.LayoutParams params = navigationController.getToolbar().getLayoutParams();
        params.height = getDimen(context, R.dimen.toolbar_height);
        navigationController.getToolbar().setLayoutParams(params);
    }

    public interface ImageViewerCallback {
        ThumbnailView getPreviewImageTransitionView(PostImage postImage);

        void scrollToImage(PostImage postImage);

        Post getPostForPostImage(PostImage postImage);

        Loadable getLoadable();
    }

    public interface GoPostCallback {
        ImageViewerCallback goToPost(PostImage postImage);
    }
}

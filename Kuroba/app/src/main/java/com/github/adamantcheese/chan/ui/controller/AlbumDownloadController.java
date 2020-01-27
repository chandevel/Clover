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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.saver.ImageSaveTask;
import com.github.adamantcheese.chan.core.saver.ImageSaver;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.view.GridRecyclerView;
import com.github.adamantcheese.chan.ui.view.PostImageThumbnailView;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.inflate;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;

public class AlbumDownloadController
        extends Controller
        implements View.OnClickListener, ImageSaver.BundledDownloadTaskCallbacks {
    private GridRecyclerView recyclerView;
    private FloatingActionButton download;

    private List<AlbumDownloadItem> items = new ArrayList<>();
    private Loadable loadable;

    @Nullable
    private LoadingViewController loadingViewController;

    @Inject
    ImageSaver imageSaver;

    private boolean allChecked = true;

    public AlbumDownloadController(Context context) {
        super(context);

        inject(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflate(context, R.layout.controller_album_download);

        updateTitle();
        navigation.buildMenu().withItem(R.drawable.ic_select_all_white_24dp, this::onCheckAllClicked).build();

        download = view.findViewById(R.id.download);
        download.setOnClickListener(this);
        ThemeHelper.getTheme().applyFabColor(download);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(context, 3);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setSpanWidth(dp(90));

        AlbumAdapter adapter = new AlbumAdapter();
        recyclerView.setAdapter(adapter);

        imageSaver.setBundledTaskCallback(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        imageSaver.removeBundleTaskCallback();
    }

    @Override
    public boolean onBack() {
        if (loadingViewController != null) {
            loadingViewController.stopPresenting();
            loadingViewController = null;
            return true;
        }
        return super.onBack();
    }

    @Override
    public void onClick(View v) {
        if (v == download) {
            int checkCount = getCheckCount();
            if (checkCount == 0) {
                showToast(R.string.album_download_none_checked);
            } else {
                String subFolder = ChanSettings.saveBoardFolder.get() ? (ChanSettings.saveThreadFolder.get()
                        ? appendAdditionalSubDirectories(items.get(0).postImage)
                        : loadable.site.name() + File.separator + loadable.boardCode) : null;
                String message = getString(
                        R.string.album_download_confirm,
                        getQuantityString(R.plurals.image, checkCount, checkCount),
                        (subFolder != null ? subFolder : "your base saved files location") + "."
                );

                //generate tasks before prompting
                List<ImageSaveTask> tasks = new ArrayList<>(items.size());
                for (AlbumDownloadItem item : items) {
                    if (item.checked) {
                        ImageSaveTask imageTask = new ImageSaveTask(loadable, item.postImage, true);
                        if (subFolder != null) {
                            imageTask.setSubFolder(subFolder);
                        }
                        tasks.add(imageTask);
                    }
                }

                new AlertDialog.Builder(context).setMessage(message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ok, (dialog, which) -> handleDownloadResult(tasks))
                        .show();
            }
        }
    }

    private void handleDownloadResult(List<ImageSaveTask> tasks) {
        ImageSaver.BundledImageSaveResult result = imageSaver.startBundledTask(context, tasks);

        switch (result) {
            case Ok:
                if (loadingViewController != null) {
                    loadingViewController.stopPresenting();
                }

                loadingViewController = new LoadingViewController(context, false);
                loadingViewController.enableBack();
                navigationController.presentController(loadingViewController);
                break;
            case BaseDirectoryDoesNotExist:
                showToast(R.string.files_base_dir_does_not_exist);
                break;
            case UnknownError:
                showToast(R.string.album_download_could_not_save_one_or_more_images);
                break;
        }
    }

    @Override
    public void onImageProcessed(int downloaded, int failed, int total) {
        if (loadingViewController != null) {
            String message =
                    getString(R.string.album_download_batch_image_processed_message, downloaded, total, failed);

            loadingViewController.updateWithText(message);
        }
    }

    @Override
    public void onBundleDownloadCompleted() {
        if (loadingViewController != null) {
            loadingViewController.stopPresenting();
            loadingViewController = null;
        }

        //extra pop to get out of this controller
        navigationController.popController();
    }

    private void onCheckAllClicked(ToolbarMenuItem menuItem) {
        RecyclerUtils.clearRecyclerCache(recyclerView);

        for (int i = 0, itemsSize = items.size(); i < itemsSize; i++) {
            AlbumDownloadItem item = items.get(i);
            if (item.checked == allChecked) {
                item.checked = !allChecked;
                AlbumDownloadCell cell = (AlbumDownloadCell) recyclerView.findViewHolderForAdapterPosition(i);
                if (cell != null) {
                    setItemChecked(cell, item.checked, true);
                }
            }
        }
        updateAllChecked();
        updateTitle();
    }

    public void setPostImages(Loadable loadable, List<PostImage> postImages) {
        this.loadable = loadable;
        for (int i = 0, postImagesSize = postImages.size(); i < postImagesSize; i++) {
            PostImage postImage = postImages.get(i);
            items.add(new AlbumDownloadItem(postImage, true, i));
        }
    }

    private void updateTitle() {
        navigation.title = getString(R.string.album_download_screen, getCheckCount(), items.size());
        ((ToolbarNavigationController) navigationController).toolbar.updateTitle(navigation);
    }

    private void updateAllChecked() {
        allChecked = getCheckCount() == items.size();
    }

    private int getCheckCount() {
        int checkCount = 0;
        for (AlbumDownloadItem item : items) {
            if (item.checked) {
                checkCount++;
            }
        }
        return checkCount;
    }

    //This method and the one in ImageViewerController should be roughly equivalent in function
    @NonNull
    private String appendAdditionalSubDirectories(PostImage postImage) {
        // save to op no appended with the first 50 characters of the subject
        // should be unique and perfectly understandable title wise
        String sanitizedSubFolderName = StringUtils.dirNameRemoveBadCharacters(loadable.site.name()) + File.separator
                + StringUtils.dirNameRemoveBadCharacters(loadable.boardCode) + File.separator + loadable.no + "_";

        String tempTitle = (loadable.no == 0 ? "catalog" : loadable.title);

        String sanitizedFileName = StringUtils.dirNameRemoveBadCharacters(tempTitle);
        String truncatedFileName = sanitizedFileName.substring(0, Math.min(sanitizedFileName.length(), 50));

        return sanitizedSubFolderName + truncatedFileName;
    }

    private static class AlbumDownloadItem {
        public PostImage postImage;
        public boolean checked;
        public int id;

        public AlbumDownloadItem(PostImage postImage, boolean checked, int id) {
            this.postImage = postImage;
            this.checked = checked;
            this.id = id;
        }
    }

    private class AlbumAdapter
            extends RecyclerView.Adapter<AlbumDownloadCell> {
        public AlbumAdapter() {
            setHasStableIds(true);
        }

        @Override
        public AlbumDownloadCell onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = inflate(parent.getContext(), R.layout.cell_album_download, parent, false);

            return new AlbumDownloadCell(view);
        }

        @Override
        public void onBindViewHolder(AlbumDownloadCell holder, int position) {
            AlbumDownloadItem item = items.get(position);

            holder.thumbnailView.setPostImage(loadable, item.postImage, true, dp(100), dp(100));
            setItemChecked(holder, item.checked, false);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }
    }

    private class AlbumDownloadCell
            extends RecyclerView.ViewHolder
            implements View.OnClickListener {
        private ImageView checkbox;
        private PostImageThumbnailView thumbnailView;

        public AlbumDownloadCell(View itemView) {
            super(itemView);
            itemView.getLayoutParams().height = recyclerView.getRealSpanWidth();
            checkbox = itemView.findViewById(R.id.checkbox);
            thumbnailView = itemView.findViewById(R.id.thumbnail_view);
            thumbnailView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int adapterPosition = getAdapterPosition();
            AlbumDownloadItem item = items.get(adapterPosition);
            item.checked = !item.checked;
            updateAllChecked();
            updateTitle();
            setItemChecked(this, item.checked, true);
        }
    }

    private void setItemChecked(AlbumDownloadCell cell, boolean checked, boolean animated) {
        float scale = checked ? 0.75f : 1f;
        if (animated) {
            Interpolator slowdown = new DecelerateInterpolator(3f);
            cell.thumbnailView.animate().scaleX(scale).scaleY(scale).setInterpolator(slowdown).setDuration(500).start();
        } else {
            cell.thumbnailView.setScaleX(scale);
            cell.thumbnailView.setScaleY(scale);
        }

        Drawable drawable = context.getDrawable(checked
                ? R.drawable.ic_blue_checkmark_24dp
                : R.drawable.ic_radio_button_unchecked_white_24dp);
        cell.checkbox.setImageDrawable(drawable);
    }
}

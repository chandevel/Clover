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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.cell.AlbumViewCell;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.view.GridRecyclerView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.skydoves.balloon.ArrowOrientation;
import com.skydoves.balloon.ArrowPositionRules;

import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;

public class AlbumViewController
        extends Controller
        implements ImageViewerController.ImageViewerCallback, ImageViewerController.GoPostCallback,
                   ToolbarNavigationController.ToolbarSearchCallback {
    private enum MenuId {
        DOWNLOAD_ALBUM
    }

    private GridRecyclerView recyclerView;

    private List<PostImage> postImages;
    private int targetIndex = -1;

    private Loadable loadable;

    public AlbumViewController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // View setup
        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_album_view, null);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setItemAnimator(null);

        AlbumAdapter albumAdapter = new AlbumAdapter(loadable);
        recyclerView.setAdapter(albumAdapter);
        recyclerView.scrollToPosition(targetIndex);
        recyclerView.getLayoutManager().setItemPrefetchEnabled(false);
    }

    public void setImages(Loadable loadable, List<PostImage> postImages, int index, String title) {
        this.loadable = loadable;
        this.postImages = postImages;

        navigation.buildMenu()
                .withItem(MenuId.DOWNLOAD_ALBUM,
                        R.drawable.ic_fluent_table_move_below_24_filled,
                        this::downloadAlbumClicked
                )
                .build();

        navigation.title = title;
        navigation.subtitle = getQuantityString(R.plurals.image, postImages.size(), postImages.size());
        targetIndex = index;
    }

    private void downloadAlbumClicked(ToolbarMenuItem item) {
        AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
        albumDownloadController.setPostImages(loadable, postImages);
        navigationController.pushController(albumDownloadController);
    }

    @Override
    public ThumbnailView getPreviewImageTransitionView(PostImage postImage) {
        ThumbnailView thumbnail = null;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View view = recyclerView.getChildAt(i);
            if (view instanceof AlbumViewCell) {
                AlbumViewCell cell = (AlbumViewCell) view;
                if (postImage == cell.getPostImage()) {
                    thumbnail = cell.getThumbnailView();
                    break;
                }
            }
        }
        return thumbnail;
    }

    @Override
    public void scrollToImage(PostImage postImage) {
        int index = postImages.indexOf(postImage);
        recyclerView.smoothScrollToPosition(index);
    }

    @Override
    public ImageViewerController.ImageViewerCallback goToPost(PostImage postImage) {
        ThreadController threadController = null;

        if (previousSiblingController instanceof DoubleNavigationController) {
            //slide or phone mode
            DoubleNavigationController doubleNav = (DoubleNavigationController) previousSiblingController;
            if (doubleNav.getRightController() instanceof ThreadController) {
                threadController = (ThreadController) doubleNav.getRightController();
            }
        } else if (previousSiblingController == null) {
            //split nav has no "sibling" to look at, so we go WAY back to find the view thread controller
            SplitNavigationController splitNav =
                    (SplitNavigationController) this.parentController.parentController.presentedByController;
            threadController = (ThreadController) splitNav.rightController.childControllers.get(0);
            threadController.selectPostImage(postImage);
            //clear the popup here because split nav is weirdly laid out in the stack
            splitNav.popController();
            return threadController;
        }

        if (threadController != null) {
            threadController.selectPostImage(postImage);
            navigationController.popController(false);
            return threadController;
        } else {
            return null;
        }
    }

    @Override
    public Post getPostForPostImage(PostImage postImage) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Loadable getLoadable() {
        return loadable;
    }

    @Override
    public void onNavItemSet() {
        AndroidUtils.getBaseToolTip(context)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setPreferenceName("DownloadAlbumHint")
                .setArrowOrientation(ArrowOrientation.TOP)
                .setTextResource(R.string.album_download_hint)
                .build()
                .showAlignBottom(navigation.findItem(MenuId.DOWNLOAD_ALBUM).getView());
    }

    private class AlbumAdapter
            extends RecyclerView.Adapter<AlbumAdapter.AlbumItemCellHolder> {
        private final Loadable loadable;

        public AlbumAdapter(Loadable loadable) {
            setHasStableIds(true);

            this.loadable = loadable;
        }

        @NonNull
        @Override
        public AlbumItemCellHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AlbumItemCellHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cell_album_view, parent, false));
        }

        @Override
        public void onBindViewHolder(AlbumItemCellHolder holder, int position) {
            holder.cell.setPostImage(postImages.get(position));
        }

        @Override
        public void onViewRecycled(@NonNull AlbumItemCellHolder holder) {
            holder.cell.setPostImage(null);
        }

        @Override
        public int getItemCount() {
            return postImages.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        private class AlbumItemCellHolder
                extends RecyclerView.ViewHolder {
            private final AlbumViewCell cell;

            public AlbumItemCellHolder(View itemView) {
                super(itemView);
                cell = (AlbumViewCell) itemView;
                cell.getLayoutParams().height = recyclerView.getRealSpanWidth();
                cell.findViewById(R.id.thumbnail_view).setOnClickListener(v -> {
                    final ImageViewerNavigationController imageViewer = new ImageViewerNavigationController(context);
                    int index = postImages.indexOf(cell.getPostImage());
                    presentController(imageViewer, false);
                    imageViewer.showImages(postImages,
                            index,
                            loadable,
                            AlbumViewController.this,
                            AlbumViewController.this
                    );
                });
            }
        }
    }
}

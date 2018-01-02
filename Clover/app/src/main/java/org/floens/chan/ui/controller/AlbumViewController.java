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

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.ui.cell.AlbumViewCell;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.GridRecyclerView;
import org.floens.chan.ui.view.PostImageThumbnailView;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class AlbumViewController extends Controller implements ImageViewerController.ImageViewerCallback, ImageViewerController.GoPostCallback, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final int SAVE_ALBUM_ID = 101;

    private GridRecyclerView recyclerView;
    private GridLayoutManager gridLayoutManager;

    private List<PostImage> postImages;
    private int targetIndex = -1;

    private AlbumAdapter albumAdapter;
    private Loadable loadable;

    public AlbumViewController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_album_view);

        navigation.menu = new ToolbarMenu(context);
        List<FloatingMenuItem> items = new ArrayList<>();
        items.add(new FloatingMenuItem(SAVE_ALBUM_ID, R.string.action_download_album));
        navigation.createOverflow(context, this, items);

        recyclerView = (GridRecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        gridLayoutManager = new GridLayoutManager(context, 3);
        recyclerView.setLayoutManager(gridLayoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setSpanWidth(dp(120));
        recyclerView.setItemAnimator(null);
        albumAdapter = new AlbumAdapter();
        recyclerView.setAdapter(albumAdapter);
        recyclerView.scrollToPosition(targetIndex);
    }

    public void setImages(Loadable loadable, List<PostImage> postImages, int index, String title) {
        this.loadable = loadable;
        this.postImages = postImages;
        navigation.title = title;
        navigation.subtitle = context.getResources().getQuantityString(R.plurals.image, postImages.size(), postImages.size());
        targetIndex = index;
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        switch ((Integer) item.getId()) {
            case SAVE_ALBUM_ID:
                AlbumDownloadController albumDownloadController = new AlbumDownloadController(context);
                albumDownloadController.setPostImages(loadable, postImages);
                navigationController.pushController(albumDownloadController);
                break;
        }
    }

    @Override
    public ThumbnailView getPreviewImageTransitionView(ImageViewerController imageViewerController, PostImage postImage) {
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
    public void onPreviewCreate(ImageViewerController imageViewerController) {
    }

    @Override
    public void onPreviewDestroy(ImageViewerController imageViewerController) {
    }

    @Override
    public void scrollToImage(PostImage postImage) {
        int index = postImages.indexOf(postImage);
        recyclerView.smoothScrollToPosition(index);
    }

    @Override
    public ImageViewerController.ImageViewerCallback goToPost(PostImage postImage) {
        ThreadController threadController = null;

        if (previousSiblingController instanceof ThreadController) {
            threadController = (ThreadController) previousSiblingController;
        } else if (previousSiblingController instanceof DoubleNavigationController) {
            DoubleNavigationController doubleNav = (DoubleNavigationController) previousSiblingController;
            if (doubleNav.getRightController() instanceof ThreadController) {
                threadController = (ThreadController) doubleNav.getRightController();
            }
        }

        if (threadController != null) {
            threadController.selectPostImage(postImage);
            navigationController.popController(false);
            return threadController;
        } else {
            return null;
        }
    }

    private void openImage(AlbumItemCellHolder albumItemCellHolder, PostImage postImage) {
        // Just ignore the showImages request when the image is not loaded
        if (albumItemCellHolder.thumbnailView.getBitmap() != null) {
            final ImageViewerNavigationController imageViewerNavigationController = new ImageViewerNavigationController(context);
            int index = postImages.indexOf(postImage);
            presentController(imageViewerNavigationController, false);
            imageViewerNavigationController.showImages(postImages, index, loadable, this, this);
        }
    }

    private class AlbumAdapter extends RecyclerView.Adapter<AlbumItemCellHolder> {
        public AlbumAdapter() {
            setHasStableIds(true);
        }

        @Override
        public AlbumItemCellHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new AlbumItemCellHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_album_view, parent, false));
        }

        @Override
        public void onBindViewHolder(AlbumItemCellHolder holder, int position) {
            PostImage postImage = postImages.get(position);
            holder.cell.setPostImage(postImage);
        }

        @Override
        public int getItemCount() {
            return postImages.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    private class AlbumItemCellHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private AlbumViewCell cell;
        private PostImageThumbnailView thumbnailView;

        public AlbumItemCellHolder(View itemView) {
            super(itemView);
            cell = (AlbumViewCell) itemView;
            thumbnailView = (PostImageThumbnailView) itemView.findViewById(R.id.thumbnail_view);
            thumbnailView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int adapterPosition = getAdapterPosition();
            PostImage postImage = postImages.get(adapterPosition);
            openImage(this, postImage);
        }
    }
}

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
package com.github.adamantcheese.chan.ui.adapter;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.saver.FileWatcher;

public class FilesAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int ITEM_TYPE_FOLDER = 0;
    private static final int ITEM_TYPE_FILE = 1;

    private FileWatcher.FileItem highlightedItem;
    private FileWatcher.FileItems fileItems;
    private final Callback callback;

    public FilesAdapter(Callback callback) {
        this.callback = callback;
    }

    public void setFiles(FileWatcher.FileItems fileItems) {
        this.fileItems = fileItems;
        notifyDataSetChanged();
    }

    public void setHighlightedItem(FileWatcher.FileItem highlightedItem) {
        this.highlightedItem = highlightedItem;
    }

    @Override
    public FileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new FileViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_file, parent, false));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int itemViewType = getItemViewType(position);
        switch (itemViewType) {
            case ITEM_TYPE_FILE:
            case ITEM_TYPE_FOLDER: {
                boolean isFile = itemViewType == ITEM_TYPE_FILE;

                FileWatcher.FileItem item = getItem(position);
                FileViewHolder fileViewHolder = ((FileViewHolder) holder);
                fileViewHolder.text.setText(item.file.getName());

                if (isFile) {
                    fileViewHolder.image.setVisibility(GONE);
                } else {
                    fileViewHolder.image.setVisibility(VISIBLE);
                }

                boolean highlighted = highlightedItem != null && highlightedItem.file.equals(item.file);
                if (highlighted) {
                    fileViewHolder.itemView.setBackgroundColor(0x0e000000);
                } else {
                    fileViewHolder.itemView.setBackgroundResource(R.drawable.ripple_item_background);
                }

                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return fileItems.fileItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        FileWatcher.FileItem item = getItem(position);
        if (item.isFile()) {
            return ITEM_TYPE_FILE;
        } else if (item.isFolder()) {
            return ITEM_TYPE_FOLDER;
        } else {
            return ITEM_TYPE_FILE;
        }
    }

    public FileWatcher.FileItem getItem(int position) {
        return fileItems.fileItems.get(position);
    }

    private void onItemClicked(FileWatcher.FileItem fileItem) {
        callback.onFileItemClicked(fileItem);
    }

    public class FileViewHolder
            extends RecyclerView.ViewHolder {
        private final ImageView image;
        private final TextView text;

        public FileViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.image);
            text = itemView.findViewById(R.id.text);
            itemView.setOnClickListener(v -> onItemClicked(getItem(getBindingAdapterPosition())));
        }
    }

    public interface Callback {
        void onFileItemClicked(FileWatcher.FileItem fileItem);
    }
}

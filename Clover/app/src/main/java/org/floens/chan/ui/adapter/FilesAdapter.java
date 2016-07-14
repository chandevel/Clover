package org.floens.chan.ui.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.model.FileItem;
import org.floens.chan.core.model.FileItems;

import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class FilesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int ITEM_TYPE_FOLDER = 0;
    private static final int ITEM_TYPE_FILE = 1;

    private FileItem highlightedItem;
    private FileItems fileItems;
    private Callback callback;

    public FilesAdapter(Callback callback) {
        this.callback = callback;
    }

    public void setFiles(FileItems fileItems) {
        this.fileItems = fileItems;
        notifyDataSetChanged();
    }

    public void setHighlightedItem(FileItem highlightedItem) {
        this.highlightedItem = highlightedItem;
    }

    @Override
    public FileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new FileViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.cell_file, parent, false));
    }

    @SuppressWarnings({"ConstantConditions", "deprecation"})
    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        int itemViewType = getItemViewType(position);
        switch (itemViewType) {
            case ITEM_TYPE_FILE:
            case ITEM_TYPE_FOLDER: {
                boolean isFile = itemViewType == ITEM_TYPE_FILE;

                FileItem item = getItem(position);
                FileViewHolder fileViewHolder = ((FileViewHolder) holder);
                fileViewHolder.text.setText(item.file.getName());

                Context context = holder.itemView.getContext();

                if (isFile) {
                    fileViewHolder.image.setVisibility(View.GONE);
                } else {
                    fileViewHolder.image.setVisibility(View.VISIBLE);
                    Drawable drawable = DrawableCompat.wrap(
                            context.getResources().getDrawable(R.drawable.ic_folder_black_24dp));
                    DrawableCompat.setTint(drawable, getAttrColor(context, R.attr.text_color_secondary));
                    fileViewHolder.image.setImageDrawable(drawable);
                }

                boolean highlighted = highlightedItem != null && highlightedItem.file.equals(item.file);
                if (highlighted) {
                    fileViewHolder.itemView.setBackgroundColor(0x0e000000);
                } else {
                    fileViewHolder.itemView.setBackgroundResource(R.drawable.item_background);
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
        FileItem item = getItem(position);
        if (item.isFile()) {
            return ITEM_TYPE_FILE;
        } else if (item.isFolder()) {
            return ITEM_TYPE_FOLDER;
        } else {
            return ITEM_TYPE_FILE;
        }
    }

    public FileItem getItem(int position) {
        return fileItems.fileItems.get(position);
    }

    private void onItemClicked(FileItem fileItem) {
        callback.onFileItemClicked(fileItem);
    }

    public class FileViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private ImageView image;
        private TextView text;

        public FileViewHolder(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.image);
            text = (TextView) itemView.findViewById(R.id.text);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            FileItem item = getItem(getAdapterPosition());
            onItemClicked(item);
        }
    }

    public interface Callback {
        void onFileItemClicked(FileItem fileItem);
    }
}

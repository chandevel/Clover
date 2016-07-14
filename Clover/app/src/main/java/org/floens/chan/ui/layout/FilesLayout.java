package org.floens.chan.ui.layout;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.model.FileItem;
import org.floens.chan.core.model.FileItems;
import org.floens.chan.ui.adapter.FilesAdapter;
import org.floens.chan.utils.RecyclerUtils;

import java.util.HashMap;
import java.util.Map;

import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class FilesLayout extends LinearLayout implements FilesAdapter.Callback, View.OnClickListener {
    private ViewGroup backLayout;
    private ImageView backImage;
    private TextView backText;
    private RecyclerView recyclerView;

    private LinearLayoutManager layoutManager;
    private FilesAdapter filesAdapter;

    private Map<String, FileItemHistory> history = new HashMap<>();
    private FileItemHistory currentHistory;
    private FileItems currentFileItems;

    private Callback callback;

    public FilesLayout(Context context) {
        this(context, null);
    }

    public FilesLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FilesLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        backLayout = (ViewGroup) findViewById(R.id.back_layout);
        backImage = (ImageView) backLayout.findViewById(R.id.back_image);
        backImage.setImageDrawable(DrawableCompat.wrap(backImage.getDrawable()));
        backText = (TextView) backLayout.findViewById(R.id.back_text);
        recyclerView = (RecyclerView) findViewById(R.id.recycler);

        backLayout.setOnClickListener(this);
    }

    public void initialize() {
        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        filesAdapter = new FilesAdapter(this);
        recyclerView.setAdapter(filesAdapter);
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public void setFiles(FileItems fileItems) {
        // Save the associated list position
        if (currentFileItems != null) {
            int[] indexTop = RecyclerUtils.getIndexAndTop(recyclerView);
            currentHistory.index = indexTop[0];
            currentHistory.top = indexTop[1];
            history.put(currentFileItems.path.getAbsolutePath(), currentHistory);
        }

        filesAdapter.setFiles(fileItems);
        currentFileItems = fileItems;

        // Restore any previous list position
        currentHistory = history.get(fileItems.path.getAbsolutePath());
        if (currentHistory != null) {
            layoutManager.scrollToPositionWithOffset(currentHistory.index, currentHistory.top);
            filesAdapter.setHighlightedItem(currentHistory.clickedItem);
        } else {
            currentHistory = new FileItemHistory();
            filesAdapter.setHighlightedItem(null);
        }

        boolean enabled = fileItems.canNavigateUp;
        backLayout.setEnabled(enabled);
        Drawable wrapped = DrawableCompat.wrap(backImage.getDrawable());
        backImage.setImageDrawable(wrapped);
        int color = getAttrColor(getContext(), enabled ? R.attr.text_color_primary : R.attr.text_color_hint);
        DrawableCompat.setTint(wrapped, color);
        backText.setEnabled(enabled);
        backText.setTextColor(color);
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    public ViewGroup getBackLayout() {
        return backLayout;
    }

    @Override
    public void onFileItemClicked(FileItem fileItem) {
        currentHistory.clickedItem = fileItem;
        callback.onFileItemClicked(fileItem);
    }

    @Override
    public void onClick(View view) {
        if (view == backLayout) {
            currentHistory.clickedItem = null;
            callback.onBackClicked();
        }
    }

    private class FileItemHistory {
        int index, top;
        FileItem clickedItem;
    }

    public interface Callback {
        void onBackClicked();

        void onFileItemClicked(FileItem fileItem);
    }
}

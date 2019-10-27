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
package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SelectLayout<T> extends LinearLayout implements SearchLayout.SearchLayoutCallback, View.OnClickListener {
    private RecyclerView recyclerView;
    private Button checkAllButton;

    private List<SelectItem<T>> items = new ArrayList<>();
    private SelectAdapter adapter;
    private boolean allChecked = false;

    public SelectLayout(Context context) {
        super(context);
    }

    public SelectLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SelectLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public void onSearchEntered(String entered) {
        adapter.search(entered);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        SearchLayout searchLayout = findViewById(R.id.search_layout);
        searchLayout.setCallback(this);

        checkAllButton = findViewById(R.id.select_all);
        checkAllButton.setOnClickListener(this);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    public void setItems(List<SelectItem<T>> items) {
        this.items.clear();
        this.items.addAll(items);

        adapter = new SelectAdapter();
        recyclerView.setAdapter(adapter);
        adapter.load();

        updateAllSelected();
    }

    public List<SelectItem<T>> getItems() {
        return items;
    }

    @Override
    public void onClick(View v) {
        if (v == checkAllButton) {
            for (SelectItem item : items) {
                item.checked = !allChecked;
            }

            updateAllSelected();
            recyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    public boolean areAllChecked() {
        return allChecked;
    }

    private void updateAllSelected() {
        int checkedCount = 0;
        for (SelectItem item : items) {
            if (item.checked) {
                checkedCount++;
            }
        }

        allChecked = checkedCount == items.size();
        checkAllButton.setText(allChecked ? R.string.board_select_none : R.string.board_select_all);
    }

    private class SelectAdapter extends RecyclerView.Adapter<BoardSelectViewHolder> {
        private List<SelectItem> sourceList = new ArrayList<>();
        private List<SelectItem> displayList = new ArrayList<>();
        private String searchQuery;

        public SelectAdapter() {
            setHasStableIds(true);
        }

        @Override
        public BoardSelectViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new BoardSelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_select, parent, false));
        }

        @Override
        public void onBindViewHolder(BoardSelectViewHolder holder, int position) {
            SelectItem item = displayList.get(position);
            holder.checkBox.setChecked(item.checked);
            holder.text.setText(item.name);
            if (item.description != null) {
                holder.description.setVisibility(View.VISIBLE);
                holder.description.setText(item.description);
            } else {
                holder.description.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return displayList.size();
        }

        @Override
        public long getItemId(int position) {
            return displayList.get(position).id;
        }

        public void search(String query) {
            this.searchQuery = query;
            filter();
        }

        private void load() {
            sourceList.clear();
            sourceList.addAll(items);

            filter();
        }

        private void filter() {
            displayList.clear();
            if (!TextUtils.isEmpty(searchQuery)) {
                String query = searchQuery.toLowerCase(Locale.ENGLISH);
                for (SelectItem item : sourceList) {
                    if (item.searchTerm.toLowerCase(Locale.ENGLISH).contains(query)) {
                        displayList.add(item);
                    }
                }
            } else {
                displayList.addAll(sourceList);
            }

            notifyDataSetChanged();
        }
    }

    private class BoardSelectViewHolder extends RecyclerView.ViewHolder implements CompoundButton.OnCheckedChangeListener, OnClickListener {
        private CheckBox checkBox;
        private TextView text;
        private TextView description;

        public BoardSelectViewHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox);
            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);

            checkBox.setOnCheckedChangeListener(this);
            checkBox.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));
            checkBox.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));

            itemView.setOnClickListener(this);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (buttonView == checkBox) {
                SelectItem board = adapter.displayList.get(getAdapterPosition());
                board.checked = isChecked;
                updateAllSelected();
            }
        }

        @Override
        public void onClick(View v) {
            checkBox.toggle();
        }
    }

    public static class SelectItem<T> {
        public final T item;
        public final long id;
        public final String name;
        public final String description;
        public final String searchTerm;
        public boolean checked;

        public SelectItem(T item, long id, String name, String description, String searchTerm, boolean checked) {
            this.item = item;
            this.id = id;
            this.name = name;
            this.description = description;
            this.searchTerm = searchTerm;
            this.checked = checked;
        }
    }
}


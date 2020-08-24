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
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuItem;
import com.github.adamantcheese.chan.ui.toolbar.ToolbarMenuSubItem;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.ui.view.DividerItemDecoration;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import static android.widget.LinearLayout.VERTICAL;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.showToast;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class HistoryController
        extends Controller
        implements ToolbarNavigationController.ToolbarSearchCallback {
    @Inject
    DatabaseManager databaseManager;

    private DatabaseLoadableManager databaseLoadableManager;
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    private CrossfadeView crossfade;
    private HistoryAdapter adapter;

    public HistoryController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inject(this);

        databaseLoadableManager = databaseManager.getDatabaseLoadableManager();
        databaseSavedReplyManager = databaseManager.getDatabaseSavedReplyManager();

        // Navigation
        navigation.setTitle(R.string.history_screen);

        navigation.buildMenu()
                .withItem(R.drawable.ic_search_white_24dp, this::searchClicked)
                .withOverflow()
                .withSubItem(R.string.saved_reply_clear, this::clearSavedReplyClicked)
                .build()
                .build();

        view = inflate(context, R.layout.controller_history);
        crossfade = view.findViewById(R.id.crossfade);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.addItemDecoration(new DividerItemDecoration(context, VERTICAL));

        adapter = new HistoryAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onShow() {
        super.onShow();
        adapter.displayList.clear();
        adapter.sourceList.clear();
        adapter.searchQuery = null;
        adapter.notifyDataSetChanged();
        adapter.load();
    }

    private void searchClicked(ToolbarMenuItem item) {
        ((ToolbarNavigationController) navigationController).showSearch();
    }

    private void clearSavedReplyClicked(ToolbarMenuSubItem item) {
        new AlertDialog.Builder(context).setTitle(R.string.saved_reply_clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(
                        R.string.saved_reply_clear_confirm_button,
                        (dialog, which) -> databaseManager.runTaskAsync(databaseSavedReplyManager.clearSavedReplies())
                )
                .show();
    }

    private void openThread(Loadable history) {
        if (history != null) {
            ViewThreadController viewThreadController = new ViewThreadController(context, history);
            navigationController.pushController(viewThreadController);
        } else {
            showToast(context, "Error opening history, null???");
        }
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        if (!visible) {
            adapter.search(null);
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        adapter.search(entered);
    }

    private class HistoryAdapter
            extends RecyclerView.Adapter<HistoryCell>
            implements DatabaseManager.TaskResult<List<Loadable>> {
        private List<Loadable> sourceList = new ArrayList<>();
        private List<Loadable> displayList = new ArrayList<>();
        private String searchQuery;

        private boolean resultPending = false;

        public HistoryAdapter() {
            setHasStableIds(true);
        }

        @Override
        public HistoryCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new HistoryCell(inflate(parent.getContext(), R.layout.cell_history, parent, false));
        }

        @Override
        public void onBindViewHolder(HistoryCell holder, int position) {
            Loadable history = displayList.get(position);
            holder.thumbnail.setUrl(history.thumbnailUrl, dp(48), dp(48));

            holder.text.setText(history.title);
            holder.subtext.setText(String.format("/%s/ – %s", history.board.code, history.board.name));
        }

        @Override
        public void onViewRecycled(@NonNull HistoryCell holder) {
            holder.thumbnail.setUrl(null);
            holder.text.setText("");
            holder.subtext.setText("");
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
            if (!resultPending) {
                resultPending = true;
                databaseManager.runTaskAsync(databaseLoadableManager.getHistory(), this);
            }
        }

        @Override
        public void onComplete(List<Loadable> result) {
            resultPending = false;
            sourceList.clear();
            sourceList.addAll(result);
            crossfade.toggle(!sourceList.isEmpty(), true);
            filter();
        }

        private void filter() {
            displayList.clear();
            if (!TextUtils.isEmpty(searchQuery)) {
                String query = searchQuery.toLowerCase(Locale.ENGLISH);
                for (Loadable history : sourceList) {
                    if (history.title.toLowerCase(Locale.ENGLISH).contains(query)) {
                        displayList.add(history);
                    }
                }
            } else {
                displayList.addAll(sourceList);
            }

            notifyDataSetChanged();
        }
    }

    private class HistoryCell
            extends RecyclerView.ViewHolder {
        private ThumbnailView thumbnail;
        private TextView text;
        private TextView subtext;

        public HistoryCell(View itemView) {
            super(itemView);

            thumbnail = itemView.findViewById(R.id.thumbnail);
            thumbnail.setCircular(true);
            text = itemView.findViewById(R.id.text);
            subtext = itemView.findViewById(R.id.subtext);

            itemView.setOnClickListener(v -> openThread(getHistory()));
        }

        private Loadable getHistory() {
            int position = getAdapterPosition();
            if (position >= 0 && position < adapter.getItemCount()) {
                return adapter.displayList.get(position);
            }
            return null;
        }
    }
}

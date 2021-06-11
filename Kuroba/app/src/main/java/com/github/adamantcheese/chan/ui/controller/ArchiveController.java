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
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.InternalSiteArchive.ArchiveItem;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.presenter.ArchivePresenter;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.ui.view.FastScrollerHelper;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ArchiveController
        extends Controller
        implements ArchivePresenter.Callback, ToolbarNavigationController.ToolbarSearchCallback,
                   SwipeRefreshLayout.OnRefreshListener {
    private SwipeRefreshLayout swipeRefreshLayout;
    private View progress;
    private View errorView;

    private final ArchivePresenter presenter;

    private ArchiveAdapter adapter;

    private final Board board;

    public ArchiveController(Context context, Board board) {
        super(context);
        this.board = board;

        presenter = new ArchivePresenter(this, board);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Inflate
        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_archive, null);

        // Navigation
        navigation.title = getString(R.string.archive_title, board.getFormattedName());
        navigation.buildMenu().withItem(R.drawable.ic_fluent_search_24_filled,
                (item) -> ((ToolbarNavigationController) navigationController).showSearch()
        ).build();

        // View binding
        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh);
        RecyclerView archiveRecyclerview = view.findViewById(R.id.recycler_view);
        progress = view.findViewById(R.id.progress);
        errorView = view.findViewById(R.id.error_text);

        // Adapters
        adapter = new ArchiveAdapter();

        // View setup
        archiveRecyclerview.setAdapter(adapter);
        archiveRecyclerview.addItemDecoration(RecyclerUtils.getBottomDividerDecoration(context));
        FastScrollerHelper.create(archiveRecyclerview);
        swipeRefreshLayout.setOnRefreshListener(this);

        //Request data
        presenter.onRefresh();
    }

    @Override
    public void onSearchEntered(String entered) {
        presenter.onSearchEntered(entered);
    }

    @Override
    public void onSearchVisibilityChanged(boolean visible) {
        presenter.onSearchVisibility(visible);
    }

    @Override
    public void onRefresh() {
        presenter.onRefresh();
    }

    @Override
    public void setArchiveItems(List<ArchiveItem> items, String filter) {
        adapter.setArchiveItems(items, filter);
    }

    @Override
    public void hideRefreshing() {
        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void showList() {
        ((CrossfadeView) view.findViewById(R.id.crossfade)).toggle(true, true);
    }

    @Override
    public void showError(boolean show) {
        progress.setVisibility(show ? GONE : VISIBLE);
        errorView.setVisibility(show ? VISIBLE : GONE);
    }

    @Override
    public void openThread(Loadable loadable) {
        ViewThreadController threadController = new ViewThreadController(context, loadable);
        navigationController.pushController(threadController);
    }

    private void onItemClicked(ArchiveItem item) {
        presenter.onItemClicked(item);
    }

    private class ArchiveAdapter
            extends RecyclerView.Adapter<ArchiveHolder> {
        private String filter = "";
        private List<ArchiveItem> archiveItems = new ArrayList<>();

        @Override
        public int getItemCount() {
            return archiveItems.size();
        }

        @Override
        public ArchiveHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ArchiveHolder(parent.getContext());
        }

        @Override
        public void onBindViewHolder(ArchiveHolder holder, int position) {
            holder.setItem(archiveItems.get(position), filter);
        }

        public void setArchiveItems(List<ArchiveItem> archiveItems, String filter) {
            this.archiveItems = archiveItems;
            this.filter = filter;
            notifyDataSetChanged();
        }
    }

    private class ArchiveHolder
            extends RecyclerView.ViewHolder {
        private ArchiveItem item;

        public ArchiveHolder(Context context) {
            super(new TextView(context));
            TextView view = (TextView) itemView;
            view.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            view.setMinimumHeight(dp(context, 48));
            view.setGravity(CENTER_VERTICAL);
            view.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
            view.setTextSize(14);
            view.setOnClickListener(v -> onItemClicked(item));
        }

        public void setItem(ArchiveItem item, String filter) {
            this.item = item;
            ((TextView) itemView).setText(StringUtils.applySearchSpans(
                    ThemeHelper.getTheme(),
                    item.description,
                    filter
            ));
        }
    }
}

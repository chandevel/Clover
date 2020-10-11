package com.github.adamantcheese.chan.ui.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseLoadableManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.ArrayList;
import java.util.List;

import static android.graphics.Typeface.BOLD;
import static android.graphics.Typeface.NORMAL;
import static android.view.Gravity.CENTER;
import static android.view.Gravity.CENTER_HORIZONTAL;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.Gravity.START;
import static android.view.Gravity.TOP;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static androidx.recyclerview.widget.RecyclerView.NO_ID;
import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;
import static com.github.adamantcheese.chan.utils.LayoutUtils.inflate;

public class DrawerHistoryAdapter
        extends RecyclerView.Adapter<DrawerHistoryAdapter.HistoryCell> {
    private List<Loadable> historyList = new ArrayList<>();
    private Callback callback;

    public DrawerHistoryAdapter(Callback callback) {
        this.callback = callback;
        historyList.add(null);
        setHasStableIds(true);
        DatabaseUtils.runTaskAsync(instance(DatabaseLoadableManager.class).getHistory(), (result) -> {
            historyList.clear();
            historyList.addAll(result);
            notifyDataSetChanged();
        });
    }

    @Override
    public HistoryCell onCreateViewHolder(ViewGroup parent, int viewType) {
        return new HistoryCell(inflate(parent.getContext(), R.layout.cell_history, parent, false));
    }

    @Override
    public void onBindViewHolder(HistoryCell holder, int position) {
        Loadable history = historyList.get(position);
        if (history != null) {
            holder.thumbnail.setUrl(history.thumbnailUrl, dp(48), dp(48));

            holder.text.setText(history.title);
            holder.subtext.setText(String.format("/%s/ – %s", history.board.code, history.board.name));
        } else {
            // all this constructs a "Loading" screen, rather than using a CrossfadeView, as the views will crossfade on a notifyDataSetChanged call
            holder.itemView.getLayoutParams().height = MATCH_PARENT;
            holder.thumbnail.setVisibility(View.GONE);
            holder.text.setText(R.string.loading);
            holder.text.setTypeface(holder.text.getTypeface(), BOLD);
            holder.text.setGravity(CENTER_VERTICAL | CENTER_HORIZONTAL);
            holder.text.getLayoutParams().height = MATCH_PARENT;
            updatePaddings(holder.text, -1, -1, dp(holder.text.getContext(), 0), -1);
            holder.subtext.setVisibility(View.GONE);
        }
    }

    @Override
    public void onViewRecycled(@NonNull HistoryCell holder) {
        // since views can be recycled, we need to take care of everything that could've occurred, including the loading screen
        holder.itemView.getLayoutParams().height = WRAP_CONTENT;
        holder.thumbnail.setVisibility(View.VISIBLE);
        holder.thumbnail.setUrl(null);
        holder.text.setText("");
        holder.text.setTypeface(holder.text.getTypeface(), NORMAL);
        holder.text.setGravity(TOP | START | CENTER);
        holder.text.getLayoutParams().height = WRAP_CONTENT;
        updatePaddings(holder.text, -1, -1, dp(holder.text.getContext(), 8), -1);
        holder.subtext.setVisibility(View.VISIBLE);
        holder.subtext.setText("");
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    @Override
    public long getItemId(int position) {
        return historyList.get(position) == null ? NO_ID : historyList.get(position).id;
    }

    protected class HistoryCell
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

            itemView.setOnClickListener(v -> callback.onHistoryClicked(getHistory()));
        }

        private Loadable getHistory() {
            int position = getAdapterPosition();
            if (position >= 0 && position < getItemCount()) {
                return historyList.get(position);
            }
            return null;
        }
    }

    public interface Callback {
        void onHistoryClicked(Loadable history);
    }
}

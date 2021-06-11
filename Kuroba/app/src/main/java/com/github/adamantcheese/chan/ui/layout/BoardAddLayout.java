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
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.presenter.BoardSetupPresenter;
import com.github.adamantcheese.chan.core.presenter.BoardSetupPresenter.BoardSuggestion;

import java.util.ArrayList;
import java.util.List;

public class BoardAddLayout
        extends LinearLayout
        implements SearchLayout.SearchLayoutCallback, BoardSetupPresenter.LayoutCallback {
    private BoardSetupPresenter presenter;

    private SuggestionsAdapter suggestionsAdapter;

    public BoardAddLayout(Context context) {
        this(context, null);
    }

    public BoardAddLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BoardAddLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // View binding
        SearchLayout search = findViewById(R.id.search);
        RecyclerView suggestionsRecycler = findViewById(R.id.suggestions);

        // Adapters
        suggestionsAdapter = new SuggestionsAdapter();

        // View setup
        search.setCallback(this);

        suggestionsRecycler.setAdapter(suggestionsAdapter);

        suggestionsRecycler.requestFocus();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            presenter.bindAddDialog(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!isInEditMode()) {
            presenter.unbindAddDialog();
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        presenter.searchEntered(entered);
    }

    @Override
    public void suggestionsWereChanged(List<BoardSuggestion> suggestionList) {
        suggestionsAdapter.setSuggestionList(suggestionList);
    }

    @Override
    public List<String> getCheckedSuggestions() {
        return suggestionsAdapter.getSelectedSuggestions();
    }

    public void setPresenter(BoardSetupPresenter presenter) {
        this.presenter = presenter;
    }

    public void onPositiveClicked() {
        presenter.onAddDialogPositiveClicked();
    }

    private class SuggestionsAdapter
            extends RecyclerView.Adapter<SuggestionCell> {

        private final List<BoardSuggestion> suggestionList = new ArrayList<>();

        public SuggestionsAdapter() {
            setHasStableIds(true);
        }

        public void setSuggestionList(List<BoardSuggestion> suggestions) {
            suggestionList.clear();
            suggestionList.addAll(suggestions);
            notifyDataSetChanged();
        }

        public List<String> getSelectedSuggestions() {
            List<String> result = new ArrayList<>();
            for (BoardSuggestion suggestion : suggestionList) {
                if (suggestion.checked) {
                    result.add(suggestion.code);
                }
            }
            return result;
        }

        @Override
        public long getItemId(int position) {
            if (isInEditMode()) return position;
            return suggestionList.get(position).getId();
        }

        @Override
        public int getItemCount() {
            if (isInEditMode()) return 15;
            return suggestionList.size();
        }

        @Override
        @NonNull
        public SuggestionCell onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SuggestionCell(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cell_board_suggestion, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SuggestionCell holder, int position) {
            holder.setSuggestion(presenter != null ? suggestionList.get(position) :
                    // this is for android studio's preview
                    new BoardSuggestion(Board.getDummyBoard()));
        }

        @Override
        public void onViewRecycled(@NonNull SuggestionCell holder) {
            holder.description.setText("");
            holder.text.setText("");
            holder.check.setOnCheckedChangeListener(null);
            holder.check.setChecked(false);
            holder.itemView.setOnClickListener(null);
            holder.suggestion = null;
        }
    }

    private static class SuggestionCell
            extends RecyclerView.ViewHolder
            implements CompoundButton.OnCheckedChangeListener {
        private final TextView text;
        private final TextView description;
        private final CheckBox check;

        private BoardSuggestion suggestion;

        public SuggestionCell(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            check = itemView.findViewById(R.id.check);
        }

        public void setSuggestion(BoardSuggestion suggestion) {
            this.suggestion = suggestion;
            text.setText(suggestion.getName());
            description.setText(suggestion.getDescription());
            check.setChecked(suggestion.isChecked());
            check.setOnCheckedChangeListener(this);
            itemView.setOnClickListener(v -> onCheckedChanged(null, false));
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            check.setOnCheckedChangeListener(null);
            itemView.setOnClickListener(null);

            suggestion.checked = !suggestion.checked;
            check.setChecked(suggestion.isChecked());

            check.setOnCheckedChangeListener(this);
            itemView.setOnClickListener(v -> onCheckedChanged(null, false));
        }
    }
}

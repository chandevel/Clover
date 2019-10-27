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
import com.github.adamantcheese.chan.core.presenter.BoardSetupPresenter;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

public class BoardAddLayout extends LinearLayout implements SearchLayout.SearchLayoutCallback, BoardSetupPresenter.AddCallback, View.OnClickListener {
    private BoardSetupPresenter presenter;

    private SuggestionsAdapter suggestionsAdapter;

    private Button checkAllButton;

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
        checkAllButton = findViewById(R.id.select_all);

        // Adapters
        suggestionsAdapter = new SuggestionsAdapter();

        // View setup
        search.setCallback(this);

        checkAllButton.setOnClickListener(this);
        suggestionsRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        suggestionsRecycler.setAdapter(suggestionsAdapter);

        suggestionsRecycler.requestFocus();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        presenter.bindAddDialog(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        presenter.unbindAddDialog();
    }

    @Override
    public void onClick(View v) {
        if (v == checkAllButton) {
            presenter.onSelectAllClicked();
        }
    }

    @Override
    public void onSearchEntered(String entered) {
        presenter.searchEntered(entered);
    }

    @Override
    public void suggestionsWereChanged() {
        suggestionsAdapter.notifyDataSetChanged();
    }

    public void setPresenter(BoardSetupPresenter presenter) {
        this.presenter = presenter;
    }

    private void onSuggestionClicked(BoardSetupPresenter.BoardSuggestion suggestion) {
        presenter.onSuggestionClicked(suggestion);
    }

    public void onPositiveClicked() {
        presenter.onAddDialogPositiveClicked();
    }

    private class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionCell> {
        public SuggestionsAdapter() {
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            return presenter.getSuggestions().get(position).getId();
        }

        @Override
        public int getItemCount() {
            return presenter.getSuggestions().size();
        }

        @Override
        public SuggestionCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SuggestionCell(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.cell_board_suggestion, parent, false));
        }

        @Override
        public void onBindViewHolder(SuggestionCell holder, int position) {
            BoardSetupPresenter.BoardSuggestion boardSuggestion = presenter.getSuggestions().get(position);
            holder.setSuggestion(boardSuggestion);
            holder.text.setText(boardSuggestion.getName());
            holder.description.setText(boardSuggestion.getDescription());
        }
    }

    private class SuggestionCell extends RecyclerView.ViewHolder implements OnClickListener, CompoundButton.OnCheckedChangeListener {
        private TextView text;
        private TextView description;
        private CheckBox check;

        private BoardSetupPresenter.BoardSuggestion suggestion;

        private boolean ignoreCheckChange = false;

        public SuggestionCell(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            check = itemView.findViewById(R.id.check);
            check.setOnCheckedChangeListener(this);
            check.setButtonTintList(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));
            check.setTextColor(ColorStateList.valueOf(ThemeHelper.getTheme().textPrimary));

            itemView.setOnClickListener(this);
        }

        public void setSuggestion(BoardSetupPresenter.BoardSuggestion suggestion) {
            this.suggestion = suggestion;
            ignoreCheckChange = true;
            check.setChecked(suggestion.isChecked());
            ignoreCheckChange = false;
        }

        @Override
        public void onClick(View v) {
            if (v == itemView) {
                toggle();
            }
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!ignoreCheckChange && buttonView == check) {
                toggle();
            }
        }

        private void toggle() {
            onSuggestionClicked(suggestion);
            ignoreCheckChange = true;
            check.setChecked(suggestion.isChecked());
            ignoreCheckChange = false;
        }
    }
}

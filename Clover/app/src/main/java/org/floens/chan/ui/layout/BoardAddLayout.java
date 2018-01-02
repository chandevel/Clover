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
package org.floens.chan.ui.layout;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.core.presenter.BoardSetupPresenter;

import static org.floens.chan.utils.AndroidUtils.getAttrColor;
import static org.floens.chan.utils.AndroidUtils.getString;

public class BoardAddLayout extends LinearLayout implements SearchLayout.SearchLayoutCallback, BoardSetupPresenter.AddCallback, View.OnClickListener {
    private BoardSetupPresenter presenter;

    private SuggestionsAdapter suggestionsAdapter;

    private SearchLayout search;
    private Button checkAllButton;
    private RecyclerView suggestionsRecycler;

    private AlertDialog dialog;

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
        search = findViewById(R.id.search);
        suggestionsRecycler = findViewById(R.id.suggestions);
        checkAllButton = findViewById(R.id.select_all);

        // Adapters
        suggestionsAdapter = new SuggestionsAdapter();

        // View setup
        search.setCallback(this);
        search.setHint(getString(R.string.search_hint));
        search.setTextColor(getAttrColor(getContext(), R.attr.text_color_primary));
        search.setHintColor(getAttrColor(getContext(), R.attr.text_color_hint));
        search.setClearButtonImage(R.drawable.ic_clear_black_24dp);
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
    public void updateSuggestions() {
        suggestionsAdapter.notifyDataSetChanged();
    }

    public void setPresenter(BoardSetupPresenter presenter) {
        this.presenter = presenter;
    }

    public void setDialog(AlertDialog dialog) {
        this.dialog = dialog;
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

    private class SuggestionCell extends RecyclerView.ViewHolder implements OnClickListener {
        private TextView text;
        private TextView description;
        private CheckBox check;

        private BoardSetupPresenter.BoardSuggestion suggestion;

        public SuggestionCell(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            check = itemView.findViewById(R.id.check);

            itemView.setOnClickListener(this);
        }

        public void setSuggestion(BoardSetupPresenter.BoardSuggestion suggestion) {
            this.suggestion = suggestion;
            check.setChecked(suggestion.isChecked());
        }

        @Override
        public void onClick(View v) {
            if (v == itemView) {
                onSuggestionClicked(suggestion);
                check.setChecked(suggestion.isChecked());
            }
        }
    }
}

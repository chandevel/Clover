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
package org.floens.chan.ui.controller;


import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.presenter.BoardSetupPresenter;
import org.floens.chan.core.site.SiteIcon;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;
import static org.floens.chan.ui.helper.PostHelper.formatBoardCodeAndName;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class BoardSetupController extends Controller implements View.OnClickListener, AdapterView.OnItemClickListener, BoardSetupPresenter.Callback {
    @Inject
    BoardSetupPresenter presenter;

    private AutoCompleteTextView code;
    private RecyclerView savedBoardsRecycler;

    private SuggestBoardsAdapter suggestAdapter;

    private SavedBoardsAdapter savedAdapter;
    private ItemTouchHelper itemTouchHelper;

    public BoardSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getGraph().inject(this);

        view = inflateRes(R.layout.controller_board_setup);
        navigationItem.setTitle(R.string.saved_boards_title);
        navigationItem.swipeable = false;

        code = (AutoCompleteTextView) view.findViewById(R.id.code);
        code.setOnItemClickListener(this);
        savedBoardsRecycler = (RecyclerView) view.findViewById(R.id.boards_recycler);
        savedBoardsRecycler.setLayoutManager(new LinearLayoutManager(context));

        savedAdapter = new SavedBoardsAdapter();
        savedBoardsRecycler.setAdapter(savedAdapter);

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(savedBoardsRecycler);

        suggestAdapter = new SuggestBoardsAdapter();

        code.setAdapter(suggestAdapter);
        code.setThreshold(1);

        presenter.create(this);
    }

    private ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
    ) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();

            presenter.move(from, to);

            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();

            presenter.remove(position);
        }
    };

    @Override
    public void setSavedBoards(List<Board> savedBoards) {
        savedAdapter.setSavedBoards(savedBoards);
    }

    @Override
    public void onClick(View v) {
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        BoardSetupPresenter.BoardSuggestion suggestion = suggestAdapter.getSuggestion(position);

        presenter.addFromSuggestion(suggestion);
    }

    private class SuggestBoardsAdapter extends BaseAdapter implements Filterable {
        private List<BoardSetupPresenter.BoardSuggestion> suggestions = new ArrayList<>();

        @Override
        public int getCount() {
            return suggestions.size();
        }

        @Override
        public String getItem(int position) {
            return getSuggestion(position).key;
        }

        public BoardSetupPresenter.BoardSuggestion getSuggestion(int position) {
            return suggestions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = LayoutInflater.from(context).inflate(R.layout.cell_board_suggestion, parent, false);

            final ImageView image = (ImageView) view.findViewById(R.id.image);
            TextView text = (TextView) view.findViewById(R.id.text);

            BoardSetupPresenter.BoardSuggestion suggestion = getSuggestion(position);

            final SiteIcon icon = suggestion.site.icon();
            icon.get(new SiteIcon.SiteIconResult() {
                @Override
                public void onSiteIcon(SiteIcon siteIcon, Drawable icon) {
                    // TODO: don't if recycled
                    image.setImageDrawable(icon);
                }
            });

            text.setText(suggestion.site.name() + " \u2013 /" + suggestion.key + "/");

            return view;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    // Invoked on a worker thread, do not use.
                    return null;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    suggestions.clear();

                    if (constraint != null) {
                        suggestions.addAll(presenter.getSuggestionsForQuery(constraint.toString()));
                    }

                    notifyDataSetChanged();
                }
            };
        }
    }

    private class SavedBoardsAdapter extends RecyclerView.Adapter<SavedBoardCell> {
        private List<Board> savedBoards;

        public SavedBoardsAdapter() {
            setHasStableIds(true);
        }

        private void setSavedBoards(List<Board> savedBoards) {
            this.savedBoards = savedBoards;
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return savedBoards.get(position).id;
        }

        @Override
        public SavedBoardCell onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SavedBoardCell(LayoutInflater.from(context).inflate(R.layout.cell_saved_board, parent, false));
        }

        @Override
        public void onBindViewHolder(SavedBoardCell holder, int position) {
            Board savedBoard = savedBoards.get(position);
            holder.setSavedBoard(savedBoard);
        }

        @Override
        public int getItemCount() {
            return savedBoards.size();
        }
    }

    private class SavedBoardCell extends RecyclerView.ViewHolder {
        private ImageView image;
        private TextView text;
        private SiteIcon siteIcon;
        private ImageView reorder;

        public SavedBoardCell(View itemView) {
            super(itemView);

            image = (ImageView) itemView.findViewById(R.id.image);
            text = (TextView) itemView.findViewById(R.id.text);
            reorder = (ImageView) itemView.findViewById(R.id.reorder);

            Drawable drawable = DrawableCompat.wrap(context.getResources().getDrawable(R.drawable.ic_reorder_black_24dp)).mutate();
            DrawableCompat.setTint(drawable, getAttrColor(context, R.attr.text_color_hint));
            reorder.setImageDrawable(drawable);

            reorder.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(SavedBoardCell.this);
                    }
                    return false;
                }
            });
        }

        public void setSavedBoard(Board savedBoard) {
            siteIcon = savedBoard.site.icon();
            siteIcon.get(new SiteIcon.SiteIconResult() {
                @Override
                public void onSiteIcon(SiteIcon siteIcon, Drawable icon) {
                    if (SavedBoardCell.this.siteIcon == siteIcon) {
                        image.setImageDrawable(icon);
                    }
                }
            });

            text.setText(formatBoardCodeAndName(savedBoard));
        }
    }
}

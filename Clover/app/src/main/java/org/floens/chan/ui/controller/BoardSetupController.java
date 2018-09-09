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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.presenter.BoardSetupPresenter;
import org.floens.chan.core.site.Site;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.ui.layout.BoardAddLayout;
import org.floens.chan.ui.view.CrossfadeView;
import org.floens.chan.ui.view.DividerItemDecoration;

import java.util.List;

import javax.inject.Inject;

import static android.text.TextUtils.isEmpty;
import static org.floens.chan.Chan.inject;
import static org.floens.chan.ui.theme.ThemeHelper.theme;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.fixSnackbarText;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class BoardSetupController extends Controller implements View.OnClickListener, BoardSetupPresenter.Callback {
    private static final int DONE_ID = 1;

    @Inject
    BoardSetupPresenter presenter;

    private CrossfadeView crossfadeView;
    private RecyclerView savedBoardsRecycler;
    private FloatingActionButton add;

    private SavedBoardsAdapter savedAdapter;
    private ItemTouchHelper itemTouchHelper;

    private Site site;

    private ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
    ) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            int from = viewHolder.getAdapterPosition();
            int to = target.getAdapterPosition();

            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return false;
            }

            presenter.move(from, to);

            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getAdapterPosition();

            presenter.remove(position);
        }
    };

    public BoardSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        inject(this);

        // Inflate
        view = inflateRes(R.layout.controller_board_setup);

        // Navigation
        navigation.title = context.getString(R.string.setup_board_title, site.name());
        navigation.swipeable = false;

        // View binding
        crossfadeView = view.findViewById(R.id.crossfade);
        savedBoardsRecycler = view.findViewById(R.id.boards_recycler);
        add = view.findViewById(R.id.add);

        // Adapters
        savedAdapter = new SavedBoardsAdapter();

        // View setup
        savedBoardsRecycler.setLayoutManager(new LinearLayoutManager(context));
        savedBoardsRecycler.setAdapter(savedAdapter);
        savedBoardsRecycler.addItemDecoration(
                new DividerItemDecoration(context, DividerItemDecoration.VERTICAL));
        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(savedBoardsRecycler);
        add.setOnClickListener(this);
        theme().applyFabColor(add);
        crossfadeView.toggle(false, false);

        // Presenter
        presenter.create(this, site);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        presenter.destroy();
    }

    public void setSite(Site site) {
        this.site = site;
    }

    @Override
    public void onClick(View v) {
        if (v == add) {
            presenter.addClicked();
        }
    }

    @Override
    public void showAddDialog() {
        @SuppressLint("InflateParams") final BoardAddLayout boardAddLayout =
                (BoardAddLayout) LayoutInflater.from(context)
                        .inflate(R.layout.layout_board_add, null);

        boardAddLayout.setPresenter(presenter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(boardAddLayout)
//                .setTitle(R.string.setup_board_add)
                .setPositiveButton(R.string.add, (dialog1, which) -> boardAddLayout.onPositiveClicked())
                .setNegativeButton(R.string.cancel, null)
                .create();

        boardAddLayout.setDialog(dialog);

        Window window = dialog.getWindow();
        assert window != null;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    @Override
    public void setSavedBoards(List<Board> savedBoards) {
        savedAdapter.setSavedBoards(savedBoards);
        crossfadeView.toggle(!savedBoards.isEmpty(), true);
    }

    @Override
    public void showRemovedSnackbar(final Board board) {
        Snackbar snackbar = Snackbar.make(view,
                context.getString(R.string.setup_board_removed, BoardHelper.getName(board)),
                Snackbar.LENGTH_LONG);
        fixSnackbarText(context, snackbar);

        snackbar.setAction(R.string.undo, v -> presenter.undoRemoveBoard(board));
        snackbar.show();
    }

    @Override
    public void boardsWereAdded(int count) {
        savedBoardsRecycler.smoothScrollToPosition(savedAdapter.getItemCount());

        String boardText = context.getResources().getQuantityString(R.plurals.board, count, count);
        String text = context.getString(R.string.setup_board_added, boardText);

        Snackbar snackbar = Snackbar.make(view, text, Snackbar.LENGTH_LONG);
        fixSnackbarText(context, snackbar);
        snackbar.show();
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
            return new SavedBoardCell(
                    LayoutInflater.from(context)
                            .inflate(R.layout.cell_board, parent, false));
        }

        @Override
        public void onBindViewHolder(SavedBoardCell holder, int position) {
            Board savedBoard = savedBoards.get(position);
            holder.text.setText(BoardHelper.getName(savedBoard));
            String description = BoardHelper.getDescription(savedBoard);
            boolean enableDescription = !isEmpty(description);
            if (enableDescription) {
                holder.description.setVisibility(View.VISIBLE);
                holder.description.setText(description);
            } else {
                holder.description.setVisibility(View.GONE);
            }

            // Fill the height for the title if there is no description, otherwise make room
            // for it.
            ViewGroup.LayoutParams p = holder.text.getLayoutParams();
            int newHeight = enableDescription ? dp(28) : dp(56);
            if (newHeight != p.height) {
                p.height = newHeight;
                holder.text.setLayoutParams(p);
            }
            holder.text.setGravity(Gravity.CENTER_VERTICAL);
            holder.text.setPadding(dp(8), dp(8), dp(8), enableDescription ? 0 : dp(8));
        }

        @Override
        public int getItemCount() {
            return savedBoards.size();
        }
    }

    private class SavedBoardCell extends RecyclerView.ViewHolder {
        private TextView text;
        private TextView description;
        private ImageView reorder;

        public SavedBoardCell(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            reorder = itemView.findViewById(R.id.reorder);

            Drawable drawable = DrawableCompat.wrap(context.getResources().getDrawable(R.drawable.ic_reorder_black_24dp)).mutate();
            DrawableCompat.setTint(drawable, getAttrColor(context, R.attr.text_color_hint));
            reorder.setImageDrawable(drawable);

			View.OnTouchListener l = (v, event) -> {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					itemTouchHelper.startDrag(SavedBoardCell.this);
				}
				return false;
			};
            reorder.setOnTouchListener(l);
        }
    }
}

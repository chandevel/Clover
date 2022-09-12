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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.updatePaddings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.presenter.BoardSetupPresenter;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.ui.layout.BoardAddLayout;
import com.github.adamantcheese.chan.ui.view.CrossfadeView;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.RecyclerUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import javax.inject.Inject;

public class BoardSetupController
        extends Controller
        implements BoardSetupPresenter.PresenterCallback {
    @Inject
    BoardSetupPresenter presenter;

    private CrossfadeView crossfadeView;
    private RecyclerView savedBoardsRecycler;

    private SavedBoardsAdapter savedAdapter;
    private ItemTouchHelper itemTouchHelper;

    private Site site;

    private final ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN,
            ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT
    ) {
        @Override
        public boolean onMove(
                @NonNull RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target
        ) {
            int from = viewHolder.getBindingAdapterPosition();
            int to = target.getBindingAdapterPosition();

            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                return false;
            }

            presenter.move(from, to);

            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            int position = viewHolder.getBindingAdapterPosition();

            presenter.removeBoard(position);
        }
    };

    public BoardSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Inflate
        view = (ViewGroup) LayoutInflater.from(context).inflate(R.layout.controller_board_setup, null);

        // Navigation
        navigation.title = getString(R.string.setup_board_title, site.name());
        navigation.swipeable = false;

        // View binding
        crossfadeView = view.findViewById(R.id.crossfade);
        savedBoardsRecycler = view.findViewById(R.id.boards_recycler);
        FloatingActionButton add = view.findViewById(R.id.add);

        // Adapters
        savedAdapter = new SavedBoardsAdapter();

        // View setup
        savedBoardsRecycler.setAdapter(savedAdapter);
        savedBoardsRecycler.addItemDecoration(RecyclerUtils.getBottomDividerDecoration(context));

        itemTouchHelper = new ItemTouchHelper(touchHelperCallback);
        itemTouchHelper.attachToRecyclerView(savedBoardsRecycler);

        add.setOnClickListener(v -> presenter.addClicked());
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
    public void showAddDialog() {
        final BoardAddLayout boardAddLayout =
                (BoardAddLayout) LayoutInflater.from(context).inflate(R.layout.layout_board_add, null);

        boardAddLayout.setPresenter(presenter);

        getDefaultAlertBuilder(context)
                .setView(boardAddLayout)
                .setPositiveButton(R.string.add, (dialog1, which) -> boardAddLayout.onPositiveClicked())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void setSavedBoards(Boards savedBoards) {
        savedAdapter.setSavedBoards(savedBoards);
        crossfadeView.toggle(!savedBoards.isEmpty(), true);
    }

    @Override
    public void showRemovedSnackbar(final Board board) {
        AndroidUtils.buildCommonSnackbar(view,
                getString(R.string.setup_board_removed, board.getFormattedName()),
                R.string.undo,
                v -> presenter.undoRemoveBoard(board)
        );
    }

    @Override
    public void boardsWereAdded(int count) {
        savedBoardsRecycler.smoothScrollToPosition(savedAdapter.getItemCount());

        AndroidUtils.buildCommonSnackbar(view,
                getString(R.string.setup_board_added, getQuantityString(R.plurals.board, count))
        );
    }

    private class SavedBoardsAdapter
            extends RecyclerView.Adapter<SavedBoardHolder> {
        private Boards savedBoards;

        public SavedBoardsAdapter() {
            setHasStableIds(true);
        }

        private void setSavedBoards(Boards savedBoards) {
            this.savedBoards = savedBoards;
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return savedBoards.get(position).id;
        }

        @NonNull
        @Override
        public SavedBoardHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new SavedBoardHolder(LayoutInflater
                    .from(parent.getContext())
                    .inflate(R.layout.cell_board, parent, false));
        }

        @Override
        public void onBindViewHolder(SavedBoardHolder holder, int position) {
            Board savedBoard = savedBoards.get(position);
            holder.text.setText(savedBoard.getFormattedName());
            if (!savedBoard.description.isEmpty()) {
                holder.description.setVisibility(VISIBLE);
                holder.description.setText(savedBoard.description);
            } else {
                holder.description.setVisibility(GONE);
            }

            // Fill the height for the title if there is no description, otherwise make room
            // for it.
            ViewGroup.LayoutParams p = holder.text.getLayoutParams();
            p.height = (int) (!savedBoard.description.isEmpty() ? dp(context, 28) : dp(context, 56));
            holder.text.setLayoutParams(p);
            holder.text.setGravity(Gravity.CENTER_VERTICAL);
            updatePaddings(holder.text, dp(context, 8), dp(context, 8), dp(context, 8), !savedBoard.description.isEmpty() ? 0 : dp(context, 8));
        }

        @Override
        public int getItemCount() {
            return savedBoards.size();
        }
    }

    private class SavedBoardHolder
            extends RecyclerView.ViewHolder {
        private final TextView text;
        private final TextView description;

        @SuppressLint("ClickableViewAccessibility")
        public SavedBoardHolder(View itemView) {
            super(itemView);

            text = itemView.findViewById(R.id.text);
            description = itemView.findViewById(R.id.description);
            ImageView reorder = itemView.findViewById(R.id.reorder);

            reorder.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(SavedBoardHolder.this);
                }
                return false;
            });
        }
    }
}

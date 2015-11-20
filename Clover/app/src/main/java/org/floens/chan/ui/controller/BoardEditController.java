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
import android.content.DialogInterface;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import com.melnykov.fab.FloatingActionButton;
import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.ui.drawable.ThumbDrawable;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.*;

import static org.floens.chan.utils.AndroidUtils.*;

public class BoardEditController extends Controller implements View.OnClickListener, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final int OPTION_SORT_A_Z = 1;

    private final BoardManager boardManager = Chan.getBoardManager();

    private RecyclerView recyclerView;
    private BoardEditAdapter adapter;
    private FloatingActionButton add;
    private ItemTouchHelper itemTouchHelper;

    private List<Board> boards;

    public BoardEditController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.setTitle(R.string.board_edit);

        List<FloatingMenuItem> items = new ArrayList<>();
        items.add(new FloatingMenuItem(OPTION_SORT_A_Z, R.string.board_edit_sort_a_z));
        navigationItem.menu = new ToolbarMenu(context);
        navigationItem.createOverflow(context, this, items);

        view = inflateRes(R.layout.controller_board_edit);
        recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        add = (FloatingActionButton) view.findViewById(R.id.add);
        add.setOnClickListener(this);
        add.attachToRecyclerView(recyclerView);

        boards = boardManager.getSavedBoards();

        adapter = new BoardEditAdapter();
        recyclerView.setAdapter(adapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                boolean isBoardItem = viewHolder.getAdapterPosition() > 0;
                int dragFlags = isBoardItem ? ItemTouchHelper.UP | ItemTouchHelper.DOWN : 0;
                int swipeFlags = isBoardItem ? ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT : 0;
                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();

                if (to > 0) {
                    Board item = boards.remove(from - 1);
                    boards.add(to - 1, item);
                    adapter.notifyItemMoved(from, to);
                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                final int position = viewHolder.getAdapterPosition();
                final Board board = boards.get(position - 1);
                board.saved = false;
                boards.remove(position - 1);
                adapter.notifyItemRemoved(position);

                Snackbar snackbar = Snackbar.make(view, context.getString(R.string.board_edit_board_removed, board.key), Snackbar.LENGTH_LONG);
                fixSnackbarText(context, snackbar);
                snackbar.setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        board.saved = true;
                        boards.add(position - 1, board);
                        adapter.notifyDataSetChanged();
                    }
                });
                snackbar.show();
            }
        });

        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        if (((Integer) item.getId()) == OPTION_SORT_A_Z) {
            Collections.sort(boards, new Comparator<Board>() {
                @Override
                public int compare(Board lhs, Board rhs) {
                    return lhs.value.compareTo(rhs.value);
                }
            });
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        for (int i = 0; i < boards.size(); i++) {
            boards.get(i).order = i;
        }

        boardManager.updateSavedBoards();
    }

    @Override
    public void onClick(View v) {
        if (v == add) {
            showAddBoardDialog();
        }
    }

    private void showAddBoardDialog() {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setPadding(dp(16), dp(16), dp(16), 0);
        final AutoCompleteTextView text = new AutoCompleteTextView(context);
        text.setSingleLine();
        wrap.addView(text, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        FillAdapter fillAdapter = new FillAdapter(context, 0);
        fillAdapter.setEditingList(boards);
        fillAdapter.setAutoCompleteView(text);
        text.setAdapter(fillAdapter);
        text.setThreshold(1);
        text.setDropDownHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        text.setHint(R.string.board_add_hint);
        text.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String value = text.getText().toString();

                        if (!TextUtils.isEmpty(value)) {
                            addBoard(value.toLowerCase(Locale.ENGLISH));
                        }
                    }
                }).setNegativeButton(R.string.cancel, null)
                .setTitle(R.string.board_add)
                .setView(wrap)
                .create();

        AndroidUtils.requestKeyboardFocus(dialog, text);

        dialog.show();
    }

    private void addBoard(String value) {
        value = value.replace(" ", "");
        value = value.replace("/", "");
        value = value.replace("\\", "");

        // Duplicate
        for (Board board : boards) {
            if (board.value.equals(value)) {
                new AlertDialog.Builder(context).setMessage(R.string.board_add_duplicate).setPositiveButton(R.string.ok, null).show();

                return;
            }
        }

        // Normal add
        List<Board> all = Chan.getBoardManager().getAllBoards();
        for (Board board : all) {
            if (board.value.equals(value)) {
                board.saved = true;
                boards.add(board);
                adapter.notifyDataSetChanged();

                recyclerView.smoothScrollToPosition(boards.size());

                Snackbar snackbar = Snackbar.make(view, getString(R.string.board_add_success) + " " + board.key, Snackbar.LENGTH_LONG);
                fixSnackbarText(context, snackbar);
                snackbar.show();

                return;
            }
        }

        // Unknown
        new AlertDialog.Builder(context)
                .setTitle(R.string.board_add_unknown_title)
                .setMessage(context.getString(R.string.board_add_unknown, value))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private class FillAdapter extends ArrayAdapter<String> implements Filterable {
        private List<Board> currentlyEditing;
        private View autoCompleteView;
        private final Filter filter;
        private final List<Board> filtered = new ArrayList<>();

        public FillAdapter(Context context, int resource) {
            super(context, resource);

            filter = new Filter() {
                @Override
                protected synchronized FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();

                    if (TextUtils.isEmpty(constraint) || (constraint.toString().startsWith(" "))) {
                        results.values = null;
                        results.count = 0;
                    } else {
                        List<Board> keys = getFiltered(constraint.toString());
                        results.values = keys;
                        results.count = keys.size();
                    }

                    return results;
                }

                @SuppressWarnings("unchecked")
                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filtered.clear();

                    if (results.values != null) {
                        filtered.addAll((List<Board>) results.values);
                    } else {
                        filtered.addAll(getBoards());
                    }

                    notifyDataSetChanged();
                }
            };
        }

        public void setEditingList(List<Board> list) {
            currentlyEditing = list;
        }

        public void setAutoCompleteView(View autoCompleteView) {
            this.autoCompleteView = autoCompleteView;
        }

        @Override
        public int getCount() {
            return filtered.size();
        }

        @Override
        public String getItem(int position) {
            return filtered.get(position).value;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            @SuppressLint("ViewHolder")
            TextView view = (TextView) LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            Board b = filtered.get(position);
            view.setText("/" + b.value + "/ - " + b.key);

            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        AndroidUtils.hideKeyboard(autoCompleteView);
                    }

                    return false;
                }
            });

            return view;
        }

        @Override
        public Filter getFilter() {
            return filter;
        }

        private List<Board> getFiltered(String filter) {
            String lowered = filter.toLowerCase(Locale.ENGLISH);
            List<Board> list = new ArrayList<>();
            for (Board b : getBoards()) {
                if ((b.key.toLowerCase(Locale.ENGLISH).contains(lowered) || b.value.toLowerCase(Locale.ENGLISH)
                        .contains(lowered))) {
                    list.add(b);
                }
            }
            return list;
        }

        private boolean haveBoard(String value) {
            for (Board b : currentlyEditing) {
                if (b.value.equals(value))
                    return true;
            }
            return false;
        }

        private List<Board> getBoards() {
            // Lets be cheaty here: if the user has nsfw boards in the list,
            // show them in the autofiller, hide them otherwise.
            boolean showUnsafe = false;
            for (Board has : currentlyEditing) {
                if (!has.workSafe) {
                    showUnsafe = true;
                    break;
                }
            }

            List<Board> s = new ArrayList<>();
            for (Board b : boardManager.getAllBoards()) {
                if (!haveBoard(b.value) && (showUnsafe || b.workSafe))
                    s.add(b);
            }
            return s;
        }
    }

    private class BoardEditAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private int TYPE_ITEM = 0;
        private int TYPE_HEADER = 1;

        public BoardEditAdapter() {
            setHasStableIds(true);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_ITEM) {
                return new BoardEditItem(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_board_edit, parent, false));
            } else {
                return new BoardEditHeader(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_board_edit_header, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_HEADER) {
                BoardEditHeader header = (BoardEditHeader) holder;
                header.text.setText(R.string.board_edit_header);
            } else {
                BoardEditItem item = (BoardEditItem) holder;
                Board board = boards.get(position - 1);
                item.text.setText(BoardHelper.getName(board));
                item.description.setText(BoardHelper.getDescription(board));
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override
        public int getItemCount() {
            return boards.size() + 1;
        }

        @Override
        public long getItemId(int position) {
            if (getItemViewType(position) == TYPE_HEADER) {
                return -1;
            } else {
                return boards.get(position - 1).id;
            }
        }
    }

    private class BoardEditItem extends RecyclerView.ViewHolder {
        private ImageView thumb;
        private TextView text;
        private TextView description;

        public BoardEditItem(View itemView) {
            super(itemView);
            thumb = (ImageView) itemView.findViewById(R.id.thumb);
            text = (TextView) itemView.findViewById(R.id.text);
            description = (TextView) itemView.findViewById(R.id.description);
            thumb.setImageDrawable(new ThumbDrawable());

            thumb.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(BoardEditItem.this);
                    }
                    return false;
                }
            });
        }
    }

    private class BoardEditHeader extends RecyclerView.ViewHolder {
        private TextView text;

        public BoardEditHeader(View itemView) {
            super(itemView);
            text = (TextView) itemView.findViewById(R.id.text);
            text.setTypeface(AndroidUtils.ROBOTO_MEDIUM_ITALIC);
        }
    }
}

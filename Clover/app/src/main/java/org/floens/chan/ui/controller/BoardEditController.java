package org.floens.chan.ui.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.ui.drawable.ThumbDrawable;
import org.floens.chan.ui.helper.SwipeListener;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.floens.chan.utils.AndroidUtils.dp;

public class BoardEditController extends Controller implements SwipeListener.Callback, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final int ADD_ID = 1;

    private final BoardManager boardManager = ChanApplication.getBoardManager();

    private RecyclerView recyclerView;
    private BoardEditAdapter adapter;

    private List<Board> boards;

    public BoardEditController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        navigationItem.title = string(R.string.board_edit);
        navigationItem.menu = new ToolbarMenu(context);
        navigationItem.menu.addItem(new ToolbarMenuItem(context, this, ADD_ID, R.drawable.ic_add_white_24dp));
        view = inflateRes(R.layout.controller_board_edit);
        recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);

        boards = boardManager.getSavedBoards();

        adapter = new BoardEditAdapter();
        recyclerView.setAdapter(adapter);

        new SwipeListener(context, recyclerView, this);
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
    public void onMenuItemClicked(ToolbarMenuItem item) {
        if ((Integer) item.getId() == ADD_ID) {
            showAddBoardDialog();
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {

    }

    @Override
    public SwipeListener.Swipeable getSwipeable(int position) {
        return (position > 0 && boards.size() > 1) ? SwipeListener.Swipeable.BOTH : SwipeListener.Swipeable.NO;
    }

    @Override
    public void removeItem(int position) {
        Board board = boards.get(position - 1);
        board.saved = false;
        boards.remove(position - 1);
        adapter.notifyItemRemoved(position);
    }

    @Override
    public boolean isMoveable(int position) {
        return position > 0;
    }

    @Override
    public void moveItem(int from, int to) {
        Board item = boards.remove(from - 1);
        boards.add(to - 1, item);
        adapter.notifyItemMoved(from, to);
    }

    @Override
    public void movingDone() {
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
        List<Board> all = ChanApplication.getBoardManager().getAllBoards();
        for (Board board : all) {
            if (board.value.equals(value)) {
                board.saved = true;
                boards.add(board);
                adapter.notifyDataSetChanged();

                new AlertDialog.Builder(context).setMessage(string(R.string.board_add_success) + " " + board.key).setPositiveButton(R.string.ok, null).show();

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
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            TextView view = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            Board b = filtered.get(position);
            view.setText("/" + b.value + "/ " + b.key);

            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(
                                Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(autoCompleteView.getWindowToken(), 0);
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
            // show them in the autofiller.
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
                item.text.setText("/" + board.value + "/ " + board.key);
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
    }

    private class BoardEditItem extends RecyclerView.ViewHolder {
        private ImageView image;
        private TextView text;

        public BoardEditItem(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.thumb);
            text = (TextView) itemView.findViewById(R.id.text);
            image.setImageDrawable(new ThumbDrawable());
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

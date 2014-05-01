package org.floens.chan.ui.activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.ui.SwipeDismissListViewTouchListener;
import org.floens.chan.utils.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;

;

public class BoardEditor extends Activity {
    private final BoardManager boardManager = ChanApplication.getBoardManager();

    private List<Board> list;
    private DragSortListView listView;
    private BoardEditAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        list = boardManager.getSavedBoards();

        adapter = new BoardEditAdapter(this, 0, list);

        listView = new DragSortListView(this, null);
        listView.setAdapter(adapter);
        listView.setDivider(new ColorDrawable(Color.TRANSPARENT));

        final DragSortController controller = new NiceDragSortController(listView, adapter);

        listView.setFloatViewManager(controller);
        listView.setOnTouchListener(controller);
        listView.setDragEnabled(true);
        listView.setDropListener(new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                if (from != to) {
                    Board board = list.remove(from);
                    list.add(to, board);
                    adapter.notifyDataSetChanged();
                }
            }
        });

        final SwipeDismissListViewTouchListener touchListener = new SwipeDismissListViewTouchListener(listView,
                new SwipeDismissListViewTouchListener.DismissCallbacks() {
                    @Override
                    public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                        for (int position : reverseSortedPositions) {
                            Board b = adapter.getItem(position);
                            adapter.remove(b);
                            b.saved = false;
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public boolean canDismiss(int position) {
                        return list.size() > 1;
                    }
                });

        listView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return controller.onTouch(view, motionEvent)
                        || (!listView.isDragging() && touchListener.onTouch(view, motionEvent));
            }
        });

        listView.setOnScrollListener(touchListener.makeScrollListener());

        setContentView(listView);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (list.size() > 0) {
            // Order
            for (int i = 0; i < list.size(); i++) {
                list.get(i).order = i;
            }

            boardManager.updateSavedBoards();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.board_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_add_board:
            showAddBoardDialog();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addBoard(final String value) {
        // Duplicate
        for (Board board : list) {
            if (board.value.equals(value)) {
                Toast.makeText(this, R.string.board_add_duplicate, Toast.LENGTH_LONG).show();

                return;
            }
        }

        // Normal add
        List<Board> all = ChanApplication.getBoardManager().getAllBoards();
        for (Board board : all) {
            if (board.value.equals(value)) {
                board.saved = true;
                list.add(board);
                adapter.notifyDataSetChanged();

                Toast.makeText(this, getString(R.string.board_add_success) + " " + board.key, Toast.LENGTH_LONG).show();

                return;
            }
        }

        // Unknown
        String message = getString(R.string.board_add_unknown).replace("CODE", value);
        new AlertDialog.Builder(this).setTitle(R.string.board_add_unknown_title).setMessage(message)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (list != null) {
                            Board b = new Board(value, value, true, true);
                            list.add(b);
                            adapter.notifyDataSetChanged();
                            boardManager.getAllBoards().add(b);
                        }
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).create().show();
    }

    private void showAddBoardDialog() {
        final AutoCompleteTextView text = new AutoCompleteTextView(this);
        text.setSingleLine();

        FillAdapter fillAdapter = new FillAdapter(this, 0);
        fillAdapter.setEditingList(list);
        fillAdapter.setAutoCompleteView(text);
        text.setAdapter(fillAdapter);
        text.setThreshold(1);
        text.setDropDownHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String value = text.getText().toString();

                        if (!TextUtils.isEmpty(value)) {
                            addBoard(value.toLowerCase(Locale.ENGLISH));
                        }
                    }
                }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                    }
                }).setTitle(R.string.board_add).setView(text).create();

        Utils.requestKeyboardFocus(dialog, text);

        dialog.show();
    }

    private static class FillAdapter extends ArrayAdapter<String> implements Filterable {
        private List<Board> currentlyEditing;
        private View autoCompleteView;
        private final Filter filter;
        private final List<Board> filtered = new ArrayList<Board>();

        public FillAdapter(Context context, int resource) {
            super(context, resource);

            filter = new Filter() {
                @Override
                protected synchronized FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();

                    if (TextUtils.isEmpty(constraint) || (constraint.toString().contains(" "))) {
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

            TextView view = (TextView) inflater.inflate(android.R.layout.simple_list_item_1, null);
            Board b = filtered.get(position);
            view.setText(b.value + " - " + b.key);

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
            List<Board> list = new ArrayList<Board>();
            for (Board b : getBoards()) {
                if (!haveBoard(b.value)
                        && (b.key.toLowerCase(Locale.ENGLISH).contains(lowered) || b.value.toLowerCase(Locale.ENGLISH)
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

            List<Board> s = new ArrayList<Board>();
            for (Board b : ChanApplication.getBoardManager().getAllBoards()) {
                if (showUnsafe || b.workSafe)
                    s.add(b);
            }
            return s;
        }
    }

    private class NiceDragSortController extends DragSortController {
        private final ListView listView;
        private final ArrayAdapter<Board> adapter;

        public NiceDragSortController(DragSortListView listView, ArrayAdapter<Board> adapter) {
            super(listView, R.id.drag_handle, DragSortController.ON_DOWN, 0);
            this.listView = listView;
            this.adapter = adapter;
            setSortEnabled(true);
            setRemoveEnabled(false);
        }

        @Override
        public View onCreateFloatView(int position) {
            return adapter.getView(position, null, listView);
        }

        @Override
        public void onDragFloatView(View floatView, Point floatPoint, Point touchPoint) {
        }

        @Override
        public void onDestroyFloatView(View floatView) {
        }
    }

    private class BoardEditAdapter extends ArrayAdapter<Board> {
        public BoardEditAdapter(Context context, int textViewResourceId, List<Board> objects) {
            super(context, textViewResourceId, objects);
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View inflated = LayoutInflater.from(getContext()).inflate(R.layout.board_edit_item, null);
            TextView text = (TextView) inflated.findViewById(R.id.text);
            Board b = getItem(position);
            text.setText(b.value + " - " + b.key);

            return inflated;
        }
    }
}

package org.floens.chan.ui.controller;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.ui.drawable.ThumbDrawable;
import org.floens.chan.ui.helper.SwipeListener;

import java.util.List;

public class BoardEditController extends Controller implements SwipeListener.Callback {
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
        view = inflateRes(R.layout.controller_board_edit);
        recyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        recyclerView.setHasFixedSize(true);

        boards = boardManager.getSavedBoards();

        adapter = new BoardEditAdapter();
        recyclerView.setAdapter(adapter);

        new SwipeListener(context, recyclerView, this);
    }

    @Override
    public SwipeListener.Swipeable getSwipeable(int position) {
        return boards.size() > 1 ? SwipeListener.Swipeable.BOTH : SwipeListener.Swipeable.NO;
    }

    @Override
    public void removeItem(int position) {

    }

    @Override
    public boolean isMoveable(int position) {
        return false;
    }

    @Override
    public void moveItem(int from, int to) {

    }

    @Override
    public void movingDone() {

    }

    private class BoardEditAdapter extends RecyclerView.Adapter<BoardEditItem> {
        @Override
        public BoardEditItem onCreateViewHolder(ViewGroup parent, int viewType) {
            return new BoardEditItem(LayoutInflater.from(parent.getContext()).inflate(R.layout.cell_board_edit, parent, false));
        }

        @Override
        public void onBindViewHolder(BoardEditItem holder, int position) {
            Board board = boards.get(position);
            holder.text.setText("/" + board.value + "/ " + board.key);
        }

        @Override
        public int getItemCount() {
            return boards.size();
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
}

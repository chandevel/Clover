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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.getAppContext;
import static org.floens.chan.utils.AndroidUtils.getRes;

public class BoardSetupController extends Controller implements View.OnClickListener {
    private AutoCompleteTextView code;
    private RecyclerView savedBoardsRecycler;

    private SavedBoardsAdapter adapter;

    public BoardSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        view = inflateRes(R.layout.controller_board_setup);
        navigationItem.setTitle(R.string.saved_boards_title);

        code = (AutoCompleteTextView) view.findViewById(R.id.code);
        savedBoardsRecycler = (RecyclerView) view.findViewById(R.id.boards_recycler);
        savedBoardsRecycler.setLayoutManager(new LinearLayoutManager(context));

        adapter = new SavedBoardsAdapter();
        savedBoardsRecycler.setAdapter(adapter);

        List<SavedBoard> savedBoards = new ArrayList<>();
        for (int board = 0; board < 5; board++) {
            savedBoards.add(new SavedBoard("foo - " + board, board));
        }

        adapter.setSavedBoards(savedBoards);

        List<String> foo = new ArrayList<String>() {{
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("foo");
            add("bar");
            add("baz");
        }};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, foo);
        code.setAdapter(adapter);
        code.setThreshold(1);
    }

    @Override
    public void onClick(View v) {
    }

    private class SavedBoard {
        private String title;
        private int id;

        public SavedBoard(String title, int id) {
            this.title = title;
            this.id = id;
        }
    }

    private class SavedBoardsAdapter extends RecyclerView.Adapter<SavedBoardCell> {
        private List<SavedBoard> savedBoards = new ArrayList<>();

        public SavedBoardsAdapter() {
            setHasStableIds(true);
        }

        private void setSavedBoards(List<SavedBoard> savedBoards) {
            this.savedBoards.clear();
            this.savedBoards.addAll(savedBoards);
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
            SavedBoard savedBoard = savedBoards.get(position);
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

        public SavedBoardCell(View itemView) {
            super(itemView);

            image = (ImageView) itemView.findViewById(R.id.image);
            text = (TextView) itemView.findViewById(R.id.text);
        }

        public void setSavedBoard(SavedBoard savedBoard) {
            Bitmap bitmap;
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inScaled = false;
                bitmap = BitmapFactory.decodeStream(getAppContext().getAssets().open("icons/4chan.png"), null, opts);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            BitmapDrawable drawable = new BitmapDrawable(getRes(), bitmap);
            drawable = (BitmapDrawable) drawable.mutate();
            drawable.getPaint().setFilterBitmap(false);

            image.setImageDrawable(drawable);
            text.setText(savedBoard.title);
        }
    }
}

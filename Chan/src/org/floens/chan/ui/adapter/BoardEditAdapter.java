/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.floens.chan.ui.adapter;

import java.util.HashMap;
import java.util.List;

import org.floens.chan.R;
import org.floens.chan.core.model.Board;
import org.floens.chan.ui.activity.BoardEditor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

public class BoardEditAdapter extends ArrayAdapter<Board> {
    HashMap<Board, Integer> mIdMap = new HashMap<Board, Integer>();
    
    private final BoardEditor editor;

    public BoardEditAdapter(Context context, int textViewResourceId, List<Board> objects, BoardEditor editor) {
        super(context, textViewResourceId, objects);
        this.editor = editor;
        
        for (int i = 0; i < objects.size(); ++i) {
            mIdMap.put(objects.get(i), i);
        }
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= mIdMap.size()) {
            return -1;
        }
        
        Board item = getItem(position);
        return mIdMap.get(item);
    }
    
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        String text = getItem(position).key;
        
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        View view = inflater.inflate(R.layout.board_view, null);
        
        TextView textView = (TextView)view.findViewById(R.id.board_view_text);
        textView.setText(text);
        
        Button button = (Button)view.findViewById(R.id.board_view_delete);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.onDeleteClicked(getItem(position));
            }
        });
        
        return view;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }
}


package org.floens.chan.activity;

import java.util.ArrayList;

import org.floens.chan.R;
import org.floens.chan.adapter.BoardEditAdapter;
import org.floens.chan.manager.BoardManager;
import org.floens.chan.model.Board;
import org.floens.chan.view.DynamicListView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.DialogInterface.OnShowListener;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class BoardEditor extends Activity {
    private ArrayList<Board> list = new ArrayList<Board>();
    private DynamicListView<Board> listView;
    private BoardEditAdapter adapter;
    private AlertDialog dialog;
    
    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.board_edit);
        
        // Copy not a reference
        list = (ArrayList<Board>) BoardManager.getInstance().getMyBoards().clone();
        
        adapter = new BoardEditAdapter(this, R.layout.board_view, list, this);
        listView = (DynamicListView<Board>) findViewById(R.id.board_edit_list);
        
        listView.setArrayList(list);
        listView.setAdapter(adapter);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    protected void onPause() {
        super.onPause();
        
        // For runtime changes
        if (list.size() > 0) {
            BoardManager.getInstance().setMyBoards((ArrayList<Board>) list.clone());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (dialog != null) {
            dialog.dismiss();
        }
    }
    
    private void addBoard(String value) {
        BoardManager.getInstance().addBoard(list, value);
        
        adapter = new BoardEditAdapter(this, R.layout.board_view, list, this);
        listView.setArrayList(list);
        listView.setAdapter(adapter);
    }
    
    private void removeBoard(String value) {
        if (list.size() <= 1) return;
        
        for (int i = 0; i < list.size(); i++) {
            Board e = list.get(i);
            if (e.value == value) {
                list.remove(i);
                
                adapter = new BoardEditAdapter(this, R.layout.board_view, list, this);
                listView.setArrayList(list);
                listView.setAdapter(adapter);
            }
        }
    }
    
    public void onDeleteClicked(Board board) {
        removeBoard(board.value);
    }
    
    private void showAddBoardDialog() {
        final EditText text = new EditText(this);
        text.setSingleLine();
        
        dialog = new AlertDialog.Builder(this)
            .setPositiveButton(R.string.board_add_confirm, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    String value = text.getText().toString();
                    
                    if (!TextUtils.isEmpty(value)) {
                        addBoard(value);
                    }
                }
            })
            .setNegativeButton(R.string.board_add_cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                }
            })
            .setTitle(R.string.board_add)
            .setView(text)
            .create();
        
        text.requestFocus();
        
        dialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                dialog = null;
            }
        });
        
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(text, 0);
            }
        });
        
        dialog.show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.board_edit, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_add_board:
            showAddBoardDialog();
            
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
}






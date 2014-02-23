package org.floens.chan.manager;

import java.util.ArrayList;
import java.util.Scanner;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.model.Board;
import org.floens.chan.net.BoardsRequest;
import org.floens.chan.net.ChanUrls;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;

public class BoardManager {
    private final Context context;
    // Including nsfw ones
    private ArrayList<Board> allBoards = new ArrayList<Board>();
    private ArrayList<Board> myBoards = new ArrayList<Board>();
    
    private final ArrayList<String> myBoardsKeys = new ArrayList<String>();
    private final ArrayList<String> myBoardsValues = new ArrayList<String>();
    
    public BoardManager(Context context) {
        this.context = context;
        
        loadFromServer();
        myBoards = loadMyBoards();
        updateMyBoardsKeysAndValues(myBoards);
    }
    
    /**
     * Avoid having 0 boards, which causes graphical problems
     * @param list
     */
    private ArrayList<Board> getDefaultBoards() {
        ArrayList<Board> list = new ArrayList<Board>();
        {
            Board board = new Board();
            board.key = "Video Games";
            board.value = "v";
            list.add(board);
        }
        {
            Board board = new Board();
            board.key = "Anime & Manga";
            board.value = "a";
            list.add(board);
        }
        {
            Board board = new Board();
            board.key = "Comics & Cartoons";
            board.value = "co";
            list.add(board);
        }
        {
            Board board = new Board();
            board.key = "Health & Fitness";
            board.value = "fit";
            list.add(board);
        }
        {
            Board board = new Board();
            board.key = "Technology";
            board.value = "g";
            list.add(board);
        }
        return list;
    }
    
    public ArrayList<Board> getMyBoards() {
        return myBoards;
    }
    
    public void setMyBoards(ArrayList<Board> list) {
        myBoards.clear();
        myBoards = list;
        updateMyBoardsKeysAndValues(list);
        storeBoardListInDatabase("myBoards", myBoards);
    }
    
    private void updateMyBoardsKeysAndValues(ArrayList<Board> list) {
        myBoardsKeys.clear();
        myBoardsValues.clear();
        
        for (Board board : list) {
            myBoardsKeys.add(board.key);
            myBoardsValues.add(board.value);
        }
    }
    
    public ArrayList<String> getMyBoardsKeys() {
        return myBoardsKeys;
    }
    
    public ArrayList<String> getMyBoardsValues() {
        return myBoardsValues;
    }
    
    public boolean getBoardExists(String board) {
        for (Board e : allBoards) {
            if (e.value.equals(board)) {
                return true;
            }
        }
        
        return false;
    }
    
    public String getBoardKey(String value) {
        for (Board e : allBoards) {
            if (e.value.equals(value)) {
                return e.key;
            }
        }
        
        return null;
    }
    
    /**
     * Try to add value to the supplied list.
     * @param list
     * @param value
     */
    public void addBoard(ArrayList<Board> list, String value) {
        for (Board board : list) {
            if (board.value.equals(value)) {
                Toast.makeText(context, R.string.board_add_duplicate, Toast.LENGTH_LONG).show();
                
                return;
            }
        }
        
        for (Board board : allBoards) {
            if (board.value.equals(value)) {
                list.add(board);
                
                String text = context.getString(R.string.board_add_success) + " " + board.key;
                Toast.makeText(context, text, Toast.LENGTH_LONG).show();
                
                return;
            }
        }
        
        Toast.makeText(context, R.string.board_add_fail, Toast.LENGTH_LONG).show();
    }
    
    private ArrayList<Board> loadMyBoards() {
        ArrayList<Board> list = getBoardListFromDatabase("myBoards");
        if (list == null || list.size() == 0) {
            list = getDefaultBoards();
            
            storeBoardListInDatabase("myBoards", list);
        }
        
        return list;
    }
    
    private void storeBoardListInDatabase(String key, ArrayList<Board> list) {
        String total = "";
        
        for (Board board : list) {
            total += board.key + "|" + board.value + "\n";
        }
        
        getPreferences().edit().putString(key, total).commit();
    }
    
    private ArrayList<Board> getBoardListFromDatabase(String key) {
        String total = getPreferences().getString(key, null);
        if (total == null) return null;
        
        ArrayList<Board> list = new ArrayList<Board>();
        
        Scanner scanner = new Scanner(total);
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            String[] splitted = line.split("\\|");
            
            if (splitted.length < 2) continue;
            
            Board board = new Board();
            board.key = splitted[0];
            board.value = splitted[1];
            if (!board.finish()) {
                Log.wtf("Chan", "board.finish in loadfrompreferences threw up");
            }
            
            list.add(board);
        }
        
        scanner.close();
        
        return list;
    }
    
    private void loadFromServer() {
        ArrayList<Board> temp = getBoardListFromDatabase("allBoards");
        if (temp != null) {
            allBoards = temp;
        }
        
        ChanApplication.getVolleyRequestQueue().add(new BoardsRequest(ChanUrls.getBoardsUrl(), new Response.Listener<ArrayList<Board>>() {
            @Override
            public void onResponse(ArrayList<Board> data) {
                storeBoardListInDatabase("allBoards", data);
                allBoards = data;
                
                Log.i("Chan", "Got boards from server");
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e("Chan", "Failed to get boards from server");
            }
        }));
    }

    private SharedPreferences getPreferences() {
        return ChanApplication.getPreferences();
    }
}






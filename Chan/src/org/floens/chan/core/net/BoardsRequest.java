package org.floens.chan.core.net;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.floens.chan.core.model.Board;

import android.util.JsonReader;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

public class BoardsRequest extends JsonReaderRequest<List<Board>> {
    public BoardsRequest(String url, Listener<List<Board>> listener, ErrorListener errorListener) {
        super(url, listener, errorListener);
    }

    @Override
    public List<Board> readJson(JsonReader reader) {
        return parseJson(reader);
    }

    private List<Board> parseJson(JsonReader reader) {
        List<Board> list = new ArrayList<Board>();

        try {
            reader.beginObject();
            // Page object
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (key.equals("boards")) {
                    reader.beginArray();

                    while (reader.hasNext()) {
                        list.add(readBoardEntry(reader));
                    }

                    reader.endArray();
                } else {
                    throw new IOException("Invalid data received");
                }
            }
            reader.endObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NumberFormatException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        return list;
    }

    @Override
    public Priority getPriority() {
        return Priority.LOW;
    }

    private Board readBoardEntry(JsonReader reader) throws IOException {
        reader.beginObject();

        Board board = new Board();

        while (reader.hasNext()) {
            String key = reader.nextName();

            if (key.equals("title")) {
                // Post number
                board.key = reader.nextString();
            } else if (key.equals("board")) {
                board.value = reader.nextString();
            } else if (key.equals("ws_board")) {
                if (reader.nextInt() == 1) {
                    board.workSafe = true;
                }
            } else {
                reader.skipValue();
            }
        }

        reader.endObject();

        if (!board.finish()) {
            throw new IOException("Invalid data received about boards.");
        }

        return board;
    }
}

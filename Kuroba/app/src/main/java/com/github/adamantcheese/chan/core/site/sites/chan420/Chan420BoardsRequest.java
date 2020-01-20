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
package com.github.adamantcheese.chan.core.site.sites.chan420;

import android.util.JsonReader;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.JsonReaderRequest;
import com.github.adamantcheese.chan.core.site.Site;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Chan420BoardsRequest extends JsonReaderRequest<List<Board>> {
    private final Site site;

    public Chan420BoardsRequest(Site site, Listener<List<Board>> listener, ErrorListener errorListener) {
        super(site.endpoints().boards().toString(), listener, errorListener);
        this.site = site;
    }

    @Override
    public List<Board> readJson(JsonReader reader) throws Exception {
        List<Board> list = new ArrayList<>();

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("boards")) {
                reader.beginArray();

                while (reader.hasNext()) {
                    Board board = readBoardEntry(reader);
                    if (board != null) {
                        list.add(board);
                    }
                }

                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return list;
    }

    private Board readBoardEntry(JsonReader reader) throws IOException {
        reader.beginObject();

        Board board = new Board();
        board.siteId = site.id();
        board.site = site;

        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "board":
                    board.code = reader.nextString();
                    break;
                case "title":
                    board.description = reader.nextString();
                    break;
                case "nws_board":
                    board.workSafe = reader.nextInt() == 1;
                    break;
                case "display_order":
                    board.order = reader.nextInt();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        if (board.hasMissingInfo()) {
            // Invalid data, ignore
            return null;
        }

        return board;
    }
}

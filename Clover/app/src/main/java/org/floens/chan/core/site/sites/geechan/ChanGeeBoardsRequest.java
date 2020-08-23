/*
 * Shishi - 4chan browser
 * Copyright (C) 2014 - 2019  Floens
 * Copyright (C) 2020 Gee
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
package org.floens.chan.core.site.sites.geechan;

import android.util.JsonReader;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.net.JsonReaderRequest;
import org.floens.chan.core.site.Site;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChanGeeBoardsRequest extends JsonReaderRequest<List<Board>> {
    private final Site site;

    ChanGeeBoardsRequest(Site site, Listener<List<Board>> listener, ErrorListener errorListener) {
        super(site.endpoints().boards().toString(), listener, errorListener);
        this.site = site;
    }

    @Override
    public List<Board> readJson(JsonReader reader) throws Exception {
        List<Board> list = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();

            while (reader.hasNext()) {
                Board board = readBoardEntry(reader);
                if (board != null) {
                    list.add(board);
                }
            }

            reader.endObject();
        }
        reader.endArray();

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
                case "title":
                    board.name = reader.nextString();
                    break;
                case "board":
                    board.code = reader.nextString();
                    board.maxFileSize = 2048 * 1024;
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        if (!board.finish()) {
            // Invalid data, ignore
            return null;
        }

        return board;
    }
}

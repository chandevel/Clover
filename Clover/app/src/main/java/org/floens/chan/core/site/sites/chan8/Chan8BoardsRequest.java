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
package org.floens.chan.core.site.sites.chan8;

import android.util.JsonReader;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.net.JsonReaderRequest;
import org.floens.chan.core.site.Site;

import java.util.ArrayList;
import java.util.List;

public class Chan8BoardsRequest extends JsonReaderRequest<List<Board>> {
    private final Site site;

    public Chan8BoardsRequest(Site site, Listener<List<Board>> listener, ErrorListener errorListener) {
        super(site.endpoints().boards().toString(), listener, errorListener);
        this.site = site;
    }

    @Override
    public List<Board> readJson(JsonReader reader) throws Exception {
        List<Board> list = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            Board b = readBoardEntry(reader);
            if (b != null) {
                list.add(b);
            }
        }
        reader.endArray();

        return list;
    }

    private Board readBoardEntry(JsonReader reader) throws Exception {
        Board board = new Board();
        board.siteId = site.id();
        board.site = site;

        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            switch (key) {
                case "uri":
                    board.code = reader.nextString();
                    break;
                case "title":
                    board.name = reader.nextString();
                    break;
                case "subtitle":
                    board.description = reader.nextString();
                    break;
                case "indexed":
                    //skip, not used
                    reader.nextString();
                    break;
                case "sfw":
                    board.workSafe = Integer.parseInt(reader.nextString()) == 1;
                    break;
                case "posts_total":
                    //skip, not used
                    reader.nextString();
                    break;
                case "time":
                    //skip, not used
                    try {
                        reader.nextNull();
                    } catch (Exception e) {
                        reader.nextString();
                    }
                    break;
                case "weight":
                    //skip, not used
                    reader.nextInt();
                    break;
                case "locale":
                    //skip, not used
                    reader.nextString();
                    break;
                case "tags":
                    reader.beginArray();
                    StringBuilder stringBuilder = new StringBuilder(board.description);
                    if (stringBuilder.length() != 0) stringBuilder.append(' ');
                    while (reader.hasNext()) {
                        stringBuilder.append(reader.nextString()).append(' ');
                    }
                    board.description = stringBuilder.toString();
                    reader.endArray();
                    break;
                case "max":
                    //skip, not used
                    reader.nextString();
                    break;
                case "active":
                    //skip, not used
                    reader.nextString();
                    break;
                case "pph":
                    //skip, not used
                    reader.nextString();
                    break;
                case "ppd":
                    //skip, not used
                    reader.nextString();
                    break;
                case "pph_average":
                    //skip, not used
                    reader.nextString();
                    break;
            }
        }
        reader.endObject();
        return board;
    }
}

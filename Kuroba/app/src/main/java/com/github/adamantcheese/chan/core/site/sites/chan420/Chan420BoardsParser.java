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

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Chan420BoardsParser
        implements NetUtilsClasses.Converter<Boards, JsonReader> {
    private final Site site;

    public Chan420BoardsParser(Site site) {
        this.site = site;
    }

    @Override
    public Boards convert(JsonReader reader)
            throws Exception {
        Boards list = new Boards();

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

    private Board readBoardEntry(JsonReader reader)
            throws IOException {
        reader.beginObject();

        Board board = new Board();
        board.siteId = site.id();
        board.site = site;

        Map<String, Integer> fileSizeLimit = new HashMap<>();
        fileSizeLimit.put("f", 40960 * 1024);
        fileSizeLimit.put("m", 40960 * 1024);
        fileSizeLimit.put("h", 40960 * 1024);
        fileSizeLimit.put("wooo", 204800 * 1024);

        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "board":
                    board.code = reader.nextString();
                    if (fileSizeLimit.containsKey(board.code)) {
                        board.maxFileSize = fileSizeLimit.get(board.code);
                    } else {
                        board.maxFileSize = 20480 * 1024;
                    }
                    break;
                case "title":
                    board.name = reader.nextString();
                    break;
                case "nws_board":
                    board.workSafe = reader.nextInt() == 1;
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

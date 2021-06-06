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
package com.github.adamantcheese.chan.core.site.sites.dvach;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;

import org.jsoup.parser.Parser;

import java.io.IOException;

public class DvachBoardsParser
        implements NetUtilsClasses.Converter<Boards, JsonReader> {
    private final Site site;

    DvachBoardsParser(Site site) {
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

        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "name":
                    board.name = reader.nextString();
                    break;
                case "id":
                    board.code = reader.nextString();
                    break;
                case "bump_limit":
                    board.bumpLimit = reader.nextInt();
                    break;
                case "info":
                    board.description = Parser.unescapeEntities(reader.nextString(), false);
                    break;
                case "category":
                    board.workSafe = !"Взрослым".equals(reader.nextString());
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        board.maxFileSize = 20480 * 1024; //20MB

        if (board.hasMissingInfo()) {
            // Invalid data, ignore
            return null;
        }
        return board;
    }
}

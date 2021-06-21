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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;

import org.jsoup.parser.Parser;

import java.io.IOException;

public class Chan4BoardsRequest
        implements NetUtilsClasses.Converter<Boards, JsonReader> {
    private final Site site;

    public Chan4BoardsRequest(Site site) {
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
                case "title":
                    board.name = reader.nextString();
                    break;
                case "board":
                    board.code = reader.nextString();
                    break;
                case "ws_board":
                    board.workSafe = reader.nextInt() == 1;
                    break;
                case "per_page":
                    board.perPage = reader.nextInt();
                    break;
                case "pages":
                    board.pages = reader.nextInt();
                    break;
                case "max_filesize":
                    board.maxFileSize = reader.nextInt();
                    break;
                case "max_webm_filesize":
                    board.maxWebmSize = reader.nextInt();
                    break;
                case "max_comment_chars":
                    board.maxCommentChars = reader.nextInt();
                    break;
                case "bump_limit":
                    board.bumpLimit = reader.nextInt();
                    break;
                case "image_limit":
                    board.imageLimit = reader.nextInt();
                    break;
                case "cooldowns":
                    reader.beginObject();

                    while (reader.hasNext()) {
                        switch (reader.nextName()) {
                            case "threads":
                                board.cooldownThreads = reader.nextInt();
                                break;
                            case "replies":
                                board.cooldownReplies = reader.nextInt();
                                break;
                            case "images":
                                board.cooldownImages = reader.nextInt();
                                break;
                            default:
                                reader.skipValue();
                                break;
                        }
                    }

                    reader.endObject();
                    break;
                case "spoilers":
                    board.spoilers = reader.nextInt() == 1;
                    break;
                case "custom_spoilers":
                    board.customSpoilers = reader.nextInt();
                    break;
                case "user_ids":
                    board.userIds = reader.nextInt() == 1;
                    break;
                case "code_tags":
                    board.codeTags = reader.nextInt() == 1;
                    break;
                case "preupload_captcha":
                    board.preuploadCaptcha = reader.nextInt() == 1;
                    break;
                case "country_flags":
                    board.countryFlags = reader.nextInt() == 1;
                    //board.boardFlags.putAll(ISO_3166_1_ALPHA_2_FLAGS.INSTANCE.getFLAG_CODES());
                    break;
                case "board_flags":
                    reader.beginObject();
                    while (reader.hasNext()) {
                        board.boardFlags.put(reader.nextName(), reader.nextString());
                    }
                    reader.endObject();
                    break;
                case "math_tags":
                    board.mathTags = reader.nextInt() == 1;
                    break;
                case "forced_anon":
                    board.forcedAnon = reader.nextInt() == 1;
                    break;
                case "meta_description":
                    board.description = Parser.unescapeEntities(reader.nextString(), false);
                    break;
                case "is_archived":
                    board.archive = reader.nextInt() == 1;
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

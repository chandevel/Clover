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
package org.floens.chan.core.net;

import android.util.JsonReader;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import org.floens.chan.core.model.Board;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BoardsRequest extends JsonReaderRequest<List<Board>> {
    public BoardsRequest(String url, Listener<List<Board>> listener, ErrorListener errorListener) {
        super(url, listener, errorListener);
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

    @Override
    public Priority getPriority() {
        return Priority.LOW;
    }

    private Board readBoardEntry(JsonReader reader) throws IOException {
        reader.beginObject();

        Board board = new Board();

        while (reader.hasNext()) {
            String key = reader.nextName();

            switch (key) {
                case "title":
                    board.key = reader.nextString();
                    break;
                case "board":
                    board.value = reader.nextString();
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
                            case "replies_intra":
                                board.cooldownRepliesIntra = reader.nextInt();
                                break;
                            case "images_intra":
                                board.cooldownImagesIntra = reader.nextInt();
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
                    break;
                case "troll_flags":
                    board.trollFlags = reader.nextInt() == 1;
                    break;
                case "math_tags":
                    board.mathTags = reader.nextInt() == 1;
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

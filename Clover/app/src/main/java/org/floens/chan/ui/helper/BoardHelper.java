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
package org.floens.chan.ui.helper;

import android.util.Pair;

import org.floens.chan.core.model.orm.Board;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import me.xdrop.fuzzywuzzy.FuzzySearch;

public class BoardHelper {
    private static final String TAG = "BoardHelper";

    public static String getName(Board board) {
        return "/" + board.code + "/ \u2013 " + board.name;
    }

    public static String getDescription(Board board) {
        return board.description == null ? null : Parser.unescapeEntities(board.description, false);
    }

    public static List<Board> quickSearch(List<Board> from, String query) {
        from = new ArrayList<>(from);
        query = query.toLowerCase();

        List<Board> res = new ArrayList<>();

        for (Iterator<Board> iterator = from.iterator(); iterator.hasNext(); ) {
            Board board = iterator.next();
            if (board.code.toLowerCase().equals(query)) {
                iterator.remove();
                res.add(board);
            }
        }

        for (Iterator<Board> iterator = from.iterator(); iterator.hasNext(); ) {
            Board board = iterator.next();
            if (board.name.toLowerCase().contains(query)) {
                iterator.remove();
                res.add(board);
            }
        }

        return res;
    }

    public static List<Board> search(List<Board> from, final String query) {
        List<Pair<Board, Integer>> ratios = new ArrayList<>();
        for (Board board : from) {
            int ratio = getTokenSortRatio(board, query);

            if (ratio > 2) {
                ratios.add(new Pair<>(board, ratio));
            }
        }

        Collections.sort(ratios, new Comparator<Pair<Board, Integer>>() {
            @Override
            public int compare(Pair<Board, Integer> o1, Pair<Board, Integer> o2) {
                return o2.second - o1.second;
            }
        });

        List<Board> result = new ArrayList<>(ratios.size());
        for (Pair<Board, Integer> ratio : ratios) {
            result.add(ratio.first);
        }
        return result;
    }

    private static int getTokenSortRatio(Board board, String query) {
        int code = FuzzySearch.ratio(board.code, query);
        int name = FuzzySearch.ratio(board.name, query);
        int description = FuzzySearch.weightedRatio(String.valueOf(getDescription(board)), query);

        return code * 4 +
                name * 5 +
                Math.max(0, description - 30) * 8;
    }

    public static String boardUniqueId(Board board) {
        String code = board.code.replace(":", "").replace(",", "");
        return board.site.id() + ":" + code;
    }

    public static boolean matchesUniqueId(Board board, String uniqueId) {
        if (!uniqueId.contains(":")) {
            return board.site.id() == 0 && board.code.equals(uniqueId);
        } else {
            String[] splitted = uniqueId.split(":");
            if (splitted.length != 2) {
                return false;
            }

            try {
                return Integer.parseInt(splitted[0]) == board.site.id() && splitted[1].equals(board.code);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
    }
}

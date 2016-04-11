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
package org.floens.chan.core.manager;

import android.text.TextUtils;

import org.floens.chan.Chan;
import org.floens.chan.core.database.DatabaseFilterManager;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.Filter;
import org.floens.chan.core.model.Post;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterEngine {
    private static final String TAG = "FilterEngine";

    private static final FilterEngine instance = new FilterEngine();

    public static FilterEngine getInstance() {
        return instance;
    }

    public enum FilterAction {
        HIDE(0),
        COLOR(1),
        REMOVE(2);

        public final int id;

        FilterAction(int id) {
            this.id = id;
        }

        public static FilterAction forId(int id) {
            return enums[id];
        }

        private static FilterAction[] enums = new FilterAction[3];

        static {
            for (FilterAction type : values()) {
                enums[type.id] = type;
            }
        }
    }

    private final DatabaseManager databaseManager;
    private final DatabaseFilterManager databaseFilterManager;

    private List<Filter> filters;
    private final List<Filter> enabledFilters = new ArrayList<>();

    private FilterEngine() {
        databaseManager = Chan.getDatabaseManager();
        databaseFilterManager = databaseManager.getDatabaseFilterManager();
        update();
    }

    public void deleteFilter(Filter filter) {
        databaseManager.runTaskSync(databaseFilterManager.deleteFilter(filter));
        update();
    }

    public void createOrUpdateFilter(Filter filter) {
        if (filter.id == 0) {
            databaseManager.runTaskSync(databaseFilterManager.createFilter(filter));
        } else {
            databaseManager.runTaskSync(databaseFilterManager.updateFilter(filter));
        }
        update();
    }

    public List<Filter> getEnabledFilters() {
        return enabledFilters;
    }

    // threadsafe
    public boolean matches(Filter filter, Post post) {
        if ((filter.type & FilterType.TRIPCODE.flag) != 0 && matches(filter, FilterType.TRIPCODE.isRegex, post.tripcode, false)) {
            return true;
        }

        if ((filter.type & FilterType.NAME.flag) != 0 && matches(filter, FilterType.NAME.isRegex, post.name, false)) {
            return true;
        }

        if ((filter.type & FilterType.COMMENT.flag) != 0 && matches(filter, FilterType.COMMENT.isRegex, post.rawComment, false)) {
            return true;
        }

        if ((filter.type & FilterType.ID.flag) != 0 && matches(filter, FilterType.ID.isRegex, post.id, false)) {
            return true;
        }

        if ((filter.type & FilterType.SUBJECT.flag) != 0 && matches(filter, FilterType.SUBJECT.isRegex, post.subject, false)) {
            return true;
        }

        if ((filter.type & FilterType.FILENAME.flag) != 0 && matches(filter, FilterType.FILENAME.isRegex, post.filename, false)) {
            return true;
        }

        return false;
    }

    // threadsafe
    public boolean matches(Filter filter, boolean matchRegex, String text, boolean forceCompile) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        if (matchRegex) {
            Matcher matcher = null;
            if (!forceCompile) {
                matcher = filter.compiledMatcher;
            }

            if (matcher == null) {
                Pattern compiledPattern = compile(filter.pattern);
                if (compiledPattern != null) {
                    matcher = filter.compiledMatcher = compiledPattern.matcher("");
                    Logger.d(TAG, "Resulting pattern: " + filter.compiledMatcher);
                }
            }

            if (matcher != null) {
                matcher.reset(text);
                try {
                    return matcher.find();
                } catch (IllegalArgumentException e) {
                    Logger.w(TAG, "matcher.find() exception", e);
                    return false;
                }
            } else {
                Logger.e(TAG, "Invalid pattern");
                return false;
            }
        } else {
            return text.equals(filter.pattern);
        }
    }

    private static final Pattern isRegexPattern = Pattern.compile("^/(.*)/(i?)$");
    private static final Pattern filterFilthyPattern = Pattern.compile("(\\.|\\^|\\$|\\*|\\+|\\?|\\(|\\)|\\[|\\]|\\{|\\}|\\\\|\\||\\-)");
    private static final Pattern wildcardPattern = Pattern.compile("\\\\\\*"); // an escaped \ and an escaped *, to replace an escaped * from escapeRegex

    // threadsafe
    public Pattern compile(String rawPattern) {
        if (TextUtils.isEmpty(rawPattern)) {
            return null;
        }

        Pattern pattern;

        Matcher isRegex = isRegexPattern.matcher(rawPattern);
        if (isRegex.matches()) {
            // This is a /Pattern/
            String flagsGroup = isRegex.group(2);
            int flags = 0;
            if (flagsGroup.contains("i")) {
                flags |= Pattern.CASE_INSENSITIVE;
            }

            try {
                pattern = Pattern.compile(isRegex.group(1), flags);
            } catch (PatternSyntaxException e) {
                return null;
            }
        } else if (rawPattern.length() >= 2 && rawPattern.charAt(0) == '"' && rawPattern.charAt(rawPattern.length() - 1) == '"') {
            // "matches an exact sentence"
            String text = escapeRegex(rawPattern.substring(1, rawPattern.length() - 1));
            pattern = Pattern.compile(text);
        } else {
            String[] words = rawPattern.split(" ");
            String text = "";
            for (int i = 0, wordsLength = words.length; i < wordsLength; i++) {
                String word = words[i];
                // Find a word (bounded by \b), replacing any * with \S*
                text += "(\\b" + (wildcardPattern.matcher(escapeRegex(word)).replaceAll("\\\\S*")) + "\\b)";
                // Allow multiple words by joining them with |
                if (i < words.length - 1) {
                    text += "|";
                }
            }

            pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
        }

        return pattern;
    }

    public List<Board> getBoardsForFilter(Filter filter) {
        if (filter.allBoards) {
            return Chan.getBoardManager().getSavedBoards();
        } else if (!TextUtils.isEmpty(filter.boards)) {
            List<Board> appliedBoards = new ArrayList<>();
            for (String value : filter.boards.split(",")) {
                Board boardByValue = Chan.getBoardManager().getBoardByCode(value);
                if (boardByValue != null) {
                    appliedBoards.add(boardByValue);
                }
            }
            return appliedBoards;
        } else {
            return Collections.emptyList();
        }
    }

    public void saveBoardsToFilter(List<Board> appliedBoards, Filter filter) {
        filter.boards = "";
        for (int i = 0; i < appliedBoards.size(); i++) {
            Board board = appliedBoards.get(i);
            filter.boards += board.code;
            if (i < appliedBoards.size() - 1) {
                filter.boards += ",";
            }
        }
    }

    private String escapeRegex(String filthy) {
        return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1"); // Escape regex special characters with a \
    }

    private void update() {
        filters = databaseManager.runTaskSync(databaseFilterManager.getFilters());
        List<Filter> enabled = new ArrayList<>();
        for (Filter filter : filters) {
            if (filter.enabled) {
                enabled.add(filter);
            }
        }

        enabledFilters.clear();
        enabledFilters.addAll(enabled);
    }
}

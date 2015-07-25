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

    public enum FilterType {
        TRIPCODE(0, false),
        NAME(1, false),
        COMMENT(2, true),
        ID(3, false),
        SUBJECT(4, true),
        FILENAME(5, true);

        public final int id;
        public final boolean isRegex;

        FilterType(int id, boolean isRegex) {
            this.id = id;
            this.isRegex = isRegex;
        }

        public static FilterType forId(int id) {
            return enums[id];
        }

        private static FilterType[] enums = new FilterType[6];

        static {
            for (FilterType type : values()) {
                enums[type.id] = type;
            }
        }
    }

    public enum FilterAction {
        HIDE(0),
        COLOR(1);

        public final int id;

        FilterAction(int id) {
            this.id = id;
        }

        public static FilterAction forId(int id) {
            return enums[id];
        }

        private static FilterAction[] enums = new FilterAction[2];

        static {
            for (FilterAction type : values()) {
                enums[type.id] = type;
            }
        }
    }

    private final DatabaseManager databaseManager;

    private List<Filter> filters;
    private final List<Filter> enabledFilters = new ArrayList<>();

    private FilterEngine() {
        databaseManager = Chan.getDatabaseManager();
        filters = databaseManager.getFilters();
        updateEnabledFilters();
    }

    /**
     * Add or update a filter, thread-safe.
     * The filter will be updated in the db if the {@link Filter#id} was non-null.
     *
     * @param filter filter too add or update.
     */
    public void addOrUpdate(Filter filter) {
        databaseManager.addOrUpdateFilter(filter);
        filters = databaseManager.getFilters();
        updateEnabledFilters();
    }

    /**
     * Remove a filter, thread-safe.
     *
     * @param filter filter to remove
     */
    public void remove(Filter filter) {
        databaseManager.removeFilter(filter);
        filters = databaseManager.getFilters();
        updateEnabledFilters();
    }

    /**
     * Get all enabled filters, thread safe if locked on {@link #getEnabledFiltersLock()}.
     *
     * @return List of enabled filters
     */
    public List<Filter> getEnabledFilters() {
        return enabledFilters;
    }

    /**
     * Lock for usage of {@link #getEnabledFilters()}
     *
     * @return Object to call synchronized on
     */
    public Object getEnabledFiltersLock() {
        return enabledFilters;
    }

    public boolean matches(Filter filter, Post post) {
        String text = null;
        FilterType type = FilterType.forId(filter.type);
        switch (type) {
            case TRIPCODE:
                text = post.tripcode;
                break;
            case NAME:
                text = post.name;
                break;
            case COMMENT:
                text = post.comment.toString();
                break;
            case ID:
                text = post.id;
                break;
            case SUBJECT:
                text = post.subject;
                break;
            case FILENAME:
                text = post.filename;
                break;
        }

        return matches(filter, text, false);
    }

    public boolean matches(Filter filter, String text, boolean forceCompile) {
        FilterType type = FilterType.forId(filter.type);
        if (type.isRegex) {
            Matcher matcher = null;
            synchronized (filter.compiledMatcherLock) {
                if (!forceCompile) {
                    matcher = filter.compiledMatcher;
                }

                if (matcher == null) {
                    Pattern compiledPattern = compile(filter.pattern);
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
    private static final Pattern filterFilthyPattern = Pattern.compile("(\\.|\\^|\\$|\\*|\\+|\\?|\\(|\\)|\\[|\\]|\\{|\\\\|\\||\\-)");
    private static final Pattern wildcardPattern = Pattern.compile("\\\\\\*"); // an escaped \ and an escaped *, to replace an escaped * from escapeRegex

    public Pattern compile(String rawPattern) {
        Pattern pattern;

        if (TextUtils.isEmpty(rawPattern)) {
            return null;
        }

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
        } else if (rawPattern.charAt(0) == '"' && rawPattern.charAt(rawPattern.length() - 1) == '"') {
            // "matches an exact sentence"
            pattern = Pattern.compile(escapeRegex(rawPattern).substring(1, rawPattern.length() - 1));
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
                Board boardByValue = Chan.getBoardManager().getBoardByValue(value);
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
            filter.boards += board.value;
            if (i < appliedBoards.size() - 1) {
                filter.boards += ",";
            }
        }
    }

    private String escapeRegex(String filthy) {
        return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1"); // Escape regex special characters with a \
    }

    private void updateEnabledFilters() {
        List<Filter> enabled = new ArrayList<>();
        for (Filter filter : filters) {
            if (filter.enabled) {
                enabled.add(filter);
            }
        }

        synchronized (enabledFilters) {
            enabledFilters.clear();
            enabledFilters.addAll(enabled);
        }
    }
}

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

import android.support.annotation.AnyThread;
import android.text.TextUtils;

import org.floens.chan.core.database.DatabaseFilterManager;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.Filter;
import org.floens.chan.ui.helper.BoardHelper;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class FilterEngine {
    private static final String TAG = "FilterEngine";

    public enum FilterAction {
        HIDE(0),
        COLOR(1),
        REMOVE(2),
        PIN(3);

        public final int id;

        FilterAction(int id) {
            this.id = id;
        }

        public static FilterAction forId(int id) {
            return enums[id];
        }

        private static FilterAction[] enums = new FilterAction[4];

        static {
            for (FilterAction type : values()) {
                enums[type.id] = type;
            }
        }
    }

    private final DatabaseManager databaseManager;
    private final BoardManager boardManager;

    private final DatabaseFilterManager databaseFilterManager;

    private final Map<String, Pattern> patternCache = new HashMap<>();
    private final List<Filter> enabledFilters = new ArrayList<>();

    @Inject
    public FilterEngine(DatabaseManager databaseManager, BoardManager boardManager) {
        this.databaseManager = databaseManager;
        this.boardManager = boardManager;
        databaseFilterManager = databaseManager.getDatabaseFilterManager();
        update();
    }

    public void deleteFilter(Filter filter) {
        databaseManager.runTask(databaseFilterManager.deleteFilter(filter));
        update();
    }

    public void createOrUpdateFilter(Filter filter) {
        if (filter.id == 0) {
            databaseManager.runTask(databaseFilterManager.createFilter(filter));
        } else {
            databaseManager.runTask(databaseFilterManager.updateFilter(filter));
        }
        update();
    }

    public List<Filter> getEnabledFilters() {
        return enabledFilters;
    }
    public List<Filter> getAllFilters() {
        try {
            return databaseFilterManager.getFilters().call();
        } catch (Exception e) {
            Logger.wtf(TAG, "Couldn't get all filters for some reason.");
            return new ArrayList<>();
        }
    }

    public List<Filter> getEnabledPinFilters() {
        List<Filter> pinFilters = new ArrayList<Filter>();
        for (Filter f : enabledFilters) {
            if(f.action == FilterAction.PIN.id) {
                pinFilters.add(f);
            }
        }
        return pinFilters;
    }

    @AnyThread
    public boolean matchesBoard(Filter filter, Board board) {
        if (filter.allBoards || TextUtils.isEmpty(filter.boards)) {
            return true;
        } else {
            for (String uniqueId : filter.boards.split(",")) {
                if (BoardHelper.matchesUniqueId(board, uniqueId)) {
                    return true;
                }
            }
            return false;
        }
    }

    public int getFilterBoardCount(Filter filter) {
        if (filter.allBoards) {
            return -1;
        } else {
            return filter.boards.split(",").length;
        }
    }

    public void saveBoardsToFilter(List<Board> appliedBoards, boolean all, Filter filter) {
        filter.allBoards = all;
        if (all) {
            filter.boards = "";
        } else {
            List<String> boardsString = new ArrayList<>(appliedBoards.size());
            for (int i = 0; i < appliedBoards.size(); i++) {
                boardsString.add(BoardHelper.boardUniqueId(appliedBoards.get(i)));
            }
            filter.boards = TextUtils.join(",", boardsString);
        }
    }

    @AnyThread
    public boolean matches(Filter filter, Post.Builder post) {
        if ((filter.type & FilterType.TRIPCODE.flag) != 0 && matches(filter, FilterType.TRIPCODE.isRegex, post.tripcode, false)) {
            return true;
        }

        if ((filter.type & FilterType.NAME.flag) != 0 && matches(filter, FilterType.NAME.isRegex, post.name, false)) {
            return true;
        }

        if ((filter.type & FilterType.COMMENT.flag) != 0 && matches(filter, FilterType.COMMENT.isRegex, post.comment.toString(), false)) {
            return true;
        }

        if ((filter.type & FilterType.ID.flag) != 0 && matches(filter, FilterType.ID.isRegex, post.posterId, false)) {
            return true;
        }

        if ((filter.type & FilterType.SUBJECT.flag) != 0 && matches(filter, FilterType.SUBJECT.isRegex, post.subject, false)) {
            return true;
        }

        if (post.images != null) {
            StringBuilder filename = new StringBuilder();
            for (PostImage image : post.images) {
                filename.append(image.filename).append(" ");
            }
            if ((filename.length() > 0) && (filter.type & FilterType.FILENAME.flag) != 0 && matches(filter, FilterType.FILENAME.isRegex, filename.toString(), false)) {
                return true;
            }
        }

        return false;
    }

    @AnyThread
    public boolean matches(Filter filter, Post post) {
        if ((filter.type & FilterType.TRIPCODE.flag) != 0 && matches(filter, FilterType.TRIPCODE.isRegex, post.tripcode, false)) {
            return true;
        }

        if ((filter.type & FilterType.NAME.flag) != 0 && matches(filter, FilterType.NAME.isRegex, post.name, false)) {
            return true;
        }

        if ((filter.type & FilterType.COMMENT.flag) != 0 && matches(filter, FilterType.COMMENT.isRegex, post.comment.toString(), false)) {
            return true;
        }

        if ((filter.type & FilterType.ID.flag) != 0 && matches(filter, FilterType.ID.isRegex, post.id, false)) {
            return true;
        }

        if ((filter.type & FilterType.SUBJECT.flag) != 0 && matches(filter, FilterType.SUBJECT.isRegex, post.subject, false)) {
            return true;
        }

        if (post.images != null) {
            StringBuilder filename = new StringBuilder();
            for (PostImage image : post.images) {
                filename.append(image.filename).append(" ");
            }
            if ((filename.length() > 0) && (filter.type & FilterType.FILENAME.flag) != 0 && matches(filter, FilterType.FILENAME.isRegex, filename.toString(), false)) {
                return true;
            }
        }

        return false;
    }

    @AnyThread
    public boolean matches(Filter filter, boolean matchRegex, String text, boolean forceCompile) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        if (matchRegex) {
            Pattern pattern = null;
            if (!forceCompile) {
                synchronized (patternCache) {
                    pattern = patternCache.get(filter.pattern);
                }
            }

            if (pattern == null) {
                pattern = compile(filter.pattern);
                if (pattern != null) {
                    synchronized (patternCache) {
                        patternCache.put(filter.pattern, pattern);
                    }
                    Logger.d(TAG, "Resulting pattern: " + pattern.pattern());
                }
            }

            if (pattern != null) {
                Matcher matcher = pattern.matcher(text);
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

    @AnyThread
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
            pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
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

    private String escapeRegex(String filthy) {
        return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1"); // Escape regex special characters with a \
    }

    private void update() {
        List<Filter> filters = databaseManager.runTask(databaseFilterManager.getFilters());
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

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
package com.github.adamantcheese.chan.core.manager;

import android.text.Html;
import android.text.TextUtils;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class FilterEngine {
    private static final String TAG = "FilterEngine";

    public enum FilterAction {
        HIDE(0),
        COLOR(1),
        REMOVE(2),
        WATCH(3);

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

        public static String actionName(FilterEngine.FilterAction action) {
            switch (action) {
                case HIDE:
                    return getString(R.string.filter_hide);
                case COLOR:
                    return getString(R.string.filter_color);
                case REMOVE:
                    return getString(R.string.filter_remove);
                case WATCH:
                    return getString(R.string.filter_watch);
            }
            return null;
        }
    }

    private final DatabaseManager databaseManager;

    private final DatabaseFilterManager databaseFilterManager;

    private final Map<String, Pattern> patternCache = new HashMap<>();
    private final List<Filter> enabledFilters = new ArrayList<>();

    @Inject
    public FilterEngine(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
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

    public List<Filter> getEnabledWatchFilters() {
        List<Filter> watchFilters = new ArrayList<>();
        for (Filter f : enabledFilters) {
            if (f.action == FilterAction.WATCH.id) {
                watchFilters.add(f);
            }
        }
        return watchFilters;
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
        if (!post.moderatorCapcode.equals("") || post.sticky) {
            return false;
        }
        if (filter.onlyOnOP && !post.op) return false;
        if ((filter.type & FilterType.TRIPCODE.flag) != 0 && matches(filter, post.tripcode, false)) {
            return true;
        }

        if ((filter.type & FilterType.NAME.flag) != 0 && matches(filter, post.name, false)) {
            return true;
        }

        if ((filter.type & FilterType.COMMENT.flag) != 0 && matches(filter, post.comment.toString(), false)) {
            return true;
        }

        if ((filter.type & FilterType.ID.flag) != 0 && matches(filter, post.posterId, false)) {
            return true;
        }

        if ((filter.type & FilterType.SUBJECT.flag) != 0 && matches(filter, post.subject, false)) {
            return true;
        }

        //figure out if the post has a country code, if so check the filter
        String countryCode = "";
        for (PostHttpIcon icon : post.httpIcons) {
            if (icon.name.indexOf('/') != -1) {
                countryCode = icon.name.substring(icon.name.indexOf('/'));
                break;
            }
        }
        if (!countryCode.isEmpty() && (filter.type & FilterType.COUNTRY_CODE.flag) != 0 && matches(filter, countryCode, false)) {
            return true;
        }

        if (post.images != null) {
            StringBuilder filename = new StringBuilder();
            for (PostImage image : post.images) {
                filename.append(image.filename).append(" ");
            }
            return (filename.length() > 0) && (filter.type & FilterType.FILENAME.flag) != 0 && matches(filter, filename.toString(), false);
        }

        return false;
    }

    @AnyThread
    public boolean matches(Filter filter, Post post) {
        if ((filter.type & FilterType.TRIPCODE.flag) != 0 && matches(filter, post.tripcode, false)) {
            return true;
        }

        if ((filter.type & FilterType.NAME.flag) != 0 && matches(filter, post.name, false)) {
            return true;
        }

        if ((filter.type & FilterType.COMMENT.flag) != 0 && matches(filter, post.comment.toString(), false)) {
            return true;
        }

        if ((filter.type & FilterType.ID.flag) != 0 && matches(filter, post.id, false)) {
            return true;
        }

        if ((filter.type & FilterType.SUBJECT.flag) != 0 && matches(filter, post.subject, false)) {
            return true;
        }

        if (post.images != null) {
            StringBuilder filename = new StringBuilder();
            for (PostImage image : post.images) {
                filename.append(image.filename).append(" ");
            }
            return (filename.length() > 0) && (filter.type & FilterType.FILENAME.flag) != 0 && matches(filter, filename.toString(), false);
        }

        return false;
    }

    @AnyThread
    public boolean matches(Filter filter, String text, boolean forceCompile) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        Pattern pattern = null;
        if (!forceCompile) {
            synchronized (patternCache) {
                pattern = patternCache.get(filter.pattern);
            }
        }

        if (pattern == null) {
            pattern = compile(filter.pattern, filter.action);
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
    }

    private static final Pattern isRegexPattern = Pattern.compile("^/(.*)/(i?)$");
    private static final Pattern filterFilthyPattern = Pattern.compile("(\\.|\\^|\\$|\\*|\\+|\\?|\\(|\\)|\\[|\\]|\\{|\\}|\\\\|\\||\\-)");
    private static final Pattern wildcardPattern = Pattern.compile("\\\\\\*"); // an escaped \ and an escaped *, to replace an escaped * from escapeRegex

    @AnyThread
    public Pattern compile(String rawPattern, int filterAction) {
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
                //the filter watch manager deals with exact comments and not HTML, so we don't escape for that
                if (filterAction != FilterAction.WATCH.id) {
                    pattern = Pattern.compile(Html.escapeHtml(isRegex.group(1)), flags);
                } else {
                    pattern = Pattern.compile(isRegex.group(1), flags);
                }
            } catch (PatternSyntaxException e) {
                return null;
            }
        } else if (rawPattern.length() >= 2 && rawPattern.charAt(0) == '"' && rawPattern.charAt(rawPattern.length() - 1) == '"') {
            // "matches an exact sentence"
            String text = escapeRegex(rawPattern.substring(1, rawPattern.length() - 1));
            if (filterAction != FilterAction.WATCH.id) {
                pattern = Pattern.compile(Html.escapeHtml(text), Pattern.CASE_INSENSITIVE);
            } else {
                pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
            }
        } else {
            String[] words = rawPattern.split(" ");
            StringBuilder text = new StringBuilder();
            for (int i = 0, wordsLength = words.length; i < wordsLength; i++) {
                String word = words[i];
                // Find a word (bounded by \b), replacing any * with \S*
                text.append("(\\b").append(wildcardPattern.matcher(escapeRegex(word)).replaceAll("\\\\S*")).append("\\b)");
                // Allow multiple words by joining them with |
                if (i < words.length - 1) {
                    text.append("|");
                }
            }

            if (filterAction != FilterAction.WATCH.id) {
                pattern = Pattern.compile(Html.escapeHtml(text.toString()), Pattern.CASE_INSENSITIVE);
            } else {
                pattern = Pattern.compile(text.toString(), Pattern.CASE_INSENSITIVE);
            }
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

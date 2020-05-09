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

import android.text.TextUtils;

import androidx.annotation.AnyThread;
import androidx.core.text.HtmlCompat;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.core.manager.FilterType.COMMENT;
import static com.github.adamantcheese.chan.core.manager.FilterType.COUNTRY_CODE;
import static com.github.adamantcheese.chan.core.manager.FilterType.FILENAME;
import static com.github.adamantcheese.chan.core.manager.FilterType.ID;
import static com.github.adamantcheese.chan.core.manager.FilterType.IMAGE;
import static com.github.adamantcheese.chan.core.manager.FilterType.NAME;
import static com.github.adamantcheese.chan.core.manager.FilterType.SUBJECT;
import static com.github.adamantcheese.chan.core.manager.FilterType.TRIPCODE;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class FilterEngine {
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

    @Inject
    public FilterEngine(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        databaseFilterManager = databaseManager.getDatabaseFilterManager();
    }

    public void deleteFilter(Filter filter) {
        databaseManager.runTask(databaseFilterManager.deleteFilter(filter));
    }

    public void createOrUpdateFilter(Filter filter) {
        if (filter.id == 0) {
            databaseManager.runTask(databaseFilterManager.createFilter(filter));
        } else {
            databaseManager.runTask(databaseFilterManager.updateFilter(filter));
        }
    }

    public List<Filter> getEnabledFilters() {
        List<Filter> filters = databaseManager.runTask(databaseFilterManager.getFilters());
        List<Filter> enabled = new ArrayList<>();
        for (Filter filter : filters) {
            if (filter.enabled) {
                enabled.add(filter);
            }
        }
        Collections.sort(enabled, (o1, o2) -> o1.order - o2.order);
        return enabled;
    }

    public List<Filter> getAllFilters() {
        try {
            return databaseFilterManager.getFilters().call();
        } catch (Exception e) {
            Logger.wtf(this, "Couldn't get all filters for some reason.");
            return new ArrayList<>();
        }
    }

    public List<Filter> getEnabledWatchFilters() {
        List<Filter> watchFilters = new ArrayList<>();
        for (Filter f : getEnabledFilters()) {
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

    /**
     * @param filter the filter to use
     * @param post   the post content to test against
     * @return true if the filter matches and should be applied to the content, false if not
     */
    @AnyThread
    public boolean matches(Filter filter, Post.Builder post) {
        if (!post.moderatorCapcode.equals("") || post.sticky) return false;
        if (filter.onlyOnOP && !post.op) return false;
        if (filter.applyToSaved && !post.isSavedReply) return false;

        if (typeMatches(filter, TRIPCODE) && matches(filter, post.tripcode, false)) return true;
        if (typeMatches(filter, NAME) && matches(filter, post.name, false)) return true;
        if (typeMatches(filter, COMMENT) && matches(filter, post.comment.toString(), false)) return true;
        if (typeMatches(filter, ID) && matches(filter, post.posterId, false)) return true;
        if (typeMatches(filter, SUBJECT) && matches(filter, post.subject, false)) return true;
        for (PostImage image : post.images) {
            if (typeMatches(filter, IMAGE) && matches(filter, image.fileHash, false)) {
                //for filtering image hashes, we don't want to apply the post-level filter (thus return false)
                //this takes care of it at an image level, either flagging it to be hidden, which applies a
                //custom spoiler image, or removes the image from the post entirely since this is a Post.Builder instance
                if (filter.action == FilterAction.HIDE.id) {
                    image.hidden = true;
                } else if (filter.action == FilterAction.REMOVE.id) {
                    post.images.remove(image);
                }
                return false;
            }
        }

        //figure out if the post has a country code, if so check the filter
        String countryCode = "";
        if (post.httpIcons != null) {
            for (PostHttpIcon icon : post.httpIcons) {
                if (icon.name.indexOf('/') != -1) {
                    countryCode = icon.name.substring(icon.name.indexOf('/') + 1);
                    break;
                }
            }
        }
        if (!countryCode.isEmpty() && typeMatches(filter, COUNTRY_CODE) && matches(filter, countryCode, false)) {
            return true;
        }

        if (post.images != null) {
            StringBuilder files = new StringBuilder();
            for (PostImage image : post.images) {
                files.append(image.filename).append(" ");
            }
            String fnames = files.toString();
            return !fnames.isEmpty() && typeMatches(filter, FILENAME) && matches(filter, fnames, false);
        }

        return false;
    }

    @AnyThread
    public boolean typeMatches(Filter filter, FilterType type) {
        return (filter.type & type.flag) != 0;
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
            int extraFlags = typeMatches(filter, COUNTRY_CODE) ? Pattern.CASE_INSENSITIVE : 0;
            pattern = compile(filter.pattern, extraFlags);
            if (pattern != null) {
                synchronized (patternCache) {
                    patternCache.put(filter.pattern, pattern);
                }
                Logger.d(this, "Resulting pattern: " + pattern.pattern());
            }
        }

        if (pattern != null) {
            Matcher matcher = pattern.matcher(HtmlCompat.fromHtml(text, 0).toString());
            try {
                return matcher.find();
            } catch (IllegalArgumentException e) {
                Logger.w(this, "matcher.find() exception", e);
                return false;
            }
        } else {
            Logger.e(this, "Invalid pattern");
            return false;
        }
    }

    private static final Pattern isRegexPattern = Pattern.compile("^/(.*)/(i?)$");
    private static final Pattern filterFilthyPattern = Pattern.compile("([.^$*+?()\\]\\[{}\\\\|-])");
    // an escaped \ and an escaped *, to replace an escaped * from escapeRegex
    private static final Pattern wildcardPattern = Pattern.compile("\\\\\\*");

    @AnyThread
    public Pattern compile(String rawPattern, int extraPatternFlags) {
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
            if (extraPatternFlags != 0) {
                flags |= extraPatternFlags;
            }

            try {
                pattern = Pattern.compile(isRegex.group(1), flags);
            } catch (PatternSyntaxException e) {
                return null;
            }
        } else if (rawPattern.length() >= 2 && rawPattern.charAt(0) == '"'
                && rawPattern.charAt(rawPattern.length() - 1) == '"') {
            // "matches an exact sentence"
            String text = escapeRegex(rawPattern.substring(1, rawPattern.length() - 1));
            pattern = Pattern.compile(text, Pattern.CASE_INSENSITIVE);
        } else {
            String[] words = rawPattern.split(" ");
            StringBuilder text = new StringBuilder();
            for (int i = 0, wordsLength = words.length; i < wordsLength; i++) {
                String word = words[i];
                // Find a word (bounded by \b), replacing any * with \S*
                text.append("(\\b")
                        .append(wildcardPattern.matcher(escapeRegex(word)).replaceAll("\\\\S*"))
                        .append("\\b)");
                // Allow multiple words by joining them with |
                if (i < words.length - 1) {
                    text.append("|");
                }
            }

            pattern = Pattern.compile(text.toString(), Pattern.CASE_INSENSITIVE);
        }

        return pattern;
    }

    private String escapeRegex(String filthy) {
        return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1"); // Escape regex special characters with a \
    }
}

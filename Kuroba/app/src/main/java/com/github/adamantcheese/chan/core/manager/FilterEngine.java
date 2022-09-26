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

import static com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction.HIDE;
import static com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction.REMOVE;
import static com.github.adamantcheese.chan.core.manager.FilterEngine.FilterAction.WATCH;
import static com.github.adamantcheese.chan.core.manager.FilterType.*;
import static com.github.adamantcheese.chan.core.site.SiteEndpoints.IconType.BOARD_FLAG;
import static com.github.adamantcheese.chan.core.site.SiteEndpoints.IconType.COUNTRY_FLAG;

import android.text.TextUtils;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.model.*;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Filters;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.j256.ormlite.dao.Dao.CreateOrUpdateStatus;

import java.util.*;
import java.util.regex.*;

public class FilterEngine {
    public enum FilterAction {
        HIDE,
        COLOR,
        REMOVE,
        WATCH;

        public static String actionName(FilterAction action) {
            return StringUtils.caseAndSpace(action.name() + " post", null, true);
        }
    }

    private final DatabaseFilterManager databaseFilterManager;

    private final Map<String, Pattern> patternCache = new HashMap<>();

    public FilterEngine(DatabaseFilterManager databaseFilterManager) {
        this.databaseFilterManager = databaseFilterManager;
    }

    public void deleteFilter(Filter filter) {
        DatabaseUtils.runTask(databaseFilterManager.deleteFilter(filter));
    }

    public CreateOrUpdateStatus createOrUpdateFilter(Filter filter) {
        return DatabaseUtils.runTask(databaseFilterManager.createOrUpdateFilter(filter));
    }

    public Filters getAllFilters() {
        try {
            return DatabaseUtils.runTask(databaseFilterManager.getFilters());
        } catch (Exception e) {
            Logger.wtf(this, "Couldn't get all filters for some reason.");
            return new Filters();
        }
    }

    public Filters getEnabledFilters() {
        Filters filters = getAllFilters();
        for (Iterator<Filter> iterator = filters.iterator(); iterator.hasNext(); ) {
            Filter filter = iterator.next();
            if (!filter.enabled) {
                iterator.remove();
            }
        }
        return filters;
    }

    public Filters getEnabledWatchFilters() {
        Filters watchFilters = getEnabledFilters();
        for (Iterator<Filter> iterator = watchFilters.iterator(); iterator.hasNext(); ) {
            Filter f = iterator.next();
            if (f.action != WATCH.ordinal()) {
                iterator.remove();
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
                if (board.matchesUniqueId(uniqueId)) {
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

    public void saveBoardsToFilter(Boards appliedBoards, boolean all, Filter filter) {
        filter.allBoards = all;
        if (all) {
            filter.boards = "";
        } else {
            List<String> boardsString = new ArrayList<>(appliedBoards.size());
            for (int i = 0; i < appliedBoards.size(); i++) {
                boardsString.add(appliedBoards.get(i).boardUniqueId());
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
        if (!post.moderatorCapcode.isEmpty() || post.sticky) return false;
        if (filter.onlyOnOP && !post.op) return false;
        if (filter.applyToSaved && !post.isSavedReply) return false;

        if (matches(filter, TRIPCODE, post.tripcode, false)) return true;
        if (matches(filter, NAME, post.getName(), false)) return true;
        if (matches(filter, COMMENT, post.comment, false)) return true;
        if (matches(filter, ID, post.posterId, false)) return true;
        if (matches(filter, SUBJECT, post.getSubject(), false)) return true;
        for (PostImage image : post.images) {
            if (matches(filter, IMAGE_HASH, image.fileHash, false)) {
                //for filtering image hashes, we don't want to apply the post-level filter unless the user set it as such
                //this takes care of it at an image level, either flagging it to be hidden, which applies a
                //custom spoiler image, or removes the image from the post entirely since this is a Post.Builder instance
                if (filter.action == HIDE.ordinal()) {
                    image.hidden = true;
                } else if (filter.action == REMOVE.ordinal()) {
                    post.images.remove(image);
                }
                return ChanSettings.applyImageFilterToPost.get();
            }
        }

        //figure out if the post has a flag code, if so check the filter
        String flagCode = "";
        for (PostHttpIcon icon : post.httpIcons) {
            if (icon.type == COUNTRY_FLAG) {
                flagCode = icon.code;
                break;
            }
            if (icon.type == BOARD_FLAG) {
                flagCode = icon.code;
                break;
            }
            }
        if (!flagCode.isEmpty() && matches(filter, FLAG_CODE, flagCode, false)) {
            return true;
        }

        if (post.images != null) {
            StringBuilder files = new StringBuilder();
            for (PostImage image : post.images) {
                files.append(image.filename).append(" ");
            }
            String fnames = files.toString();
            return !fnames.isEmpty() && matches(filter, FILENAME, fnames, false);
        }

        return false;
    }

    public boolean matches(Filter filter, FilterType type, CharSequence text, boolean forceCompile) {
        return getMatchResult(filter, type, text, forceCompile) != null;
    }

    @AnyThread
    public MatchResult getMatchResult(Filter filter, FilterType type, CharSequence text, boolean forceCompile) {
        if ((filter.type & type.flag) == 0) return null;
        if (text == null) return null;

        Pattern pattern = null;
        if (!forceCompile) {
            synchronized (patternCache) {
                pattern = patternCache.get(filter.pattern);
            }
        }

        if (pattern == null) {
            int extraFlags = type == FLAG_CODE ? Pattern.CASE_INSENSITIVE : 0;
            pattern = compile(filter.pattern, extraFlags);
            if (pattern != null) {
                synchronized (patternCache) {
                    patternCache.put(filter.pattern, pattern);
                }
                Logger.d(this, "Resulting pattern: " + pattern.pattern());
            }
        }

        if (pattern != null) {
            Matcher matcher = pattern.matcher(text);
            boolean matched = matcher.find();
            return matched ? matcher.toMatchResult() : null;
        } else {
            Logger.e(this, "Invalid pattern");
            return null;
        }
    }

    private static final Pattern isRegexPattern = Pattern.compile("^/(.*)/([mi]*)?$");
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
            if (flagsGroup.contains("m")) {
                flags |= Pattern.MULTILINE;
            }
            if (extraPatternFlags != 0) {
                flags |= extraPatternFlags;
            }

            try {
                //Don't allow an empty regex string (would match everything)
                pattern = isRegex.group(1).length() > 0 ? Pattern.compile(isRegex.group(1), flags) : null;
            } catch (PatternSyntaxException e) {
                return null;
            }
        } else if (rawPattern.length() >= 2
                && rawPattern.charAt(0) == '"'
                && rawPattern.charAt(rawPattern.length() - 1) == '"') {
            // "matches an exact sentence"
            String text = escapeRegex(rawPattern.substring(1, rawPattern.length() - 1));
            //Don't allow only double quotes (would match everything)
            pattern = rawPattern.length() != 2 ? Pattern.compile(text, Pattern.CASE_INSENSITIVE) : null;
        } else {
            String[] words = rawPattern.split(" ");
            StringBuilder text = new StringBuilder();
            for (int i = 0, wordsLength = words.length; i < wordsLength; i++) {
                String word = words[i];
                // Find a word (bounded by \b), replacing any * with \S*
                text
                        .append("(\\b")
                        .append(wildcardPattern.matcher(escapeRegex(word)).replaceAll("\\\\S*"))
                        .append("\\b)");
                // Allow multiple words by joining them with |
                if (i < words.length - 1) {
                    text.append("|");
                }
            }
            //Don't allow only spaces (would match everything after split)
            pattern = !TextUtils.isEmpty(text) ? Pattern.compile(text.toString(), Pattern.CASE_INSENSITIVE) : null;
        }

        return pattern;
    }

    public static String escapeRegex(String filthy) {
        return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1"); // Escape regex special characters with a \
    }
}

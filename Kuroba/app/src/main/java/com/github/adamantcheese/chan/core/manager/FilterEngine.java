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

import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.database.DatabaseFilterManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteEndpoints;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Boards;
import com.github.adamantcheese.chan.ui.helper.BoardHelper;
import com.github.adamantcheese.chan.ui.text.FilterHighlightSpan;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.github.adamantcheese.chan.core.manager.FilterType.COMMENT;
import static com.github.adamantcheese.chan.core.manager.FilterType.FILENAME;
import static com.github.adamantcheese.chan.core.manager.FilterType.FLAG_CODE;
import static com.github.adamantcheese.chan.core.manager.FilterType.ID;
import static com.github.adamantcheese.chan.core.manager.FilterType.IMAGE;
import static com.github.adamantcheese.chan.core.manager.FilterType.NAME;
import static com.github.adamantcheese.chan.core.manager.FilterType.SUBJECT;
import static com.github.adamantcheese.chan.core.manager.FilterType.TRIPCODE;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
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

        private static final FilterAction[] enums = new FilterAction[4];

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

    private final DatabaseFilterManager databaseFilterManager;

    private final Map<String, Pattern> patternCache = new HashMap<>();

    public FilterEngine(DatabaseFilterManager databaseFilterManager) {
        this.databaseFilterManager = databaseFilterManager;
    }

    public void deleteFilter(Filter filter) {
        DatabaseUtils.runTask(databaseFilterManager.deleteFilter(filter));
    }

    public void createOrUpdateFilter(Filter filter) {
        if (filter.id == 0) {
            DatabaseUtils.runTask(databaseFilterManager.createFilter(filter));
        } else {
            DatabaseUtils.runTask(databaseFilterManager.updateFilter(filter));
        }
    }

    public List<Filter> getEnabledFilters() {
        List<Filter> filters = DatabaseUtils.runTask(databaseFilterManager.getFilters());
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
            return DatabaseUtils.runTask(databaseFilterManager.getFilters());
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

    public void saveBoardsToFilter(Boards appliedBoards, boolean all, Filter filter) {
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
        if (!post.moderatorCapcode.isEmpty() || post.sticky) return false;
        if (filter.onlyOnOP && !post.op) return false;
        if (filter.applyToSaved && !post.isSavedReply) return false;

        if (matches(filter, TRIPCODE, post.tripcode, false)) return true;
        if (matches(filter, NAME, post.name, false)) return true;
        if (matches(filter, COMMENT, post.comment, false)) return true;
        if (matches(filter, ID, post.posterId, false)) return true;
        if (matches(filter, SUBJECT, post.subject, false)) return true;
        for (PostImage image : post.images) {
            if (matches(filter, IMAGE, image.fileHash, false)) {
                //for filtering image hashes, we don't want to apply the post-level filter unless the user set it as such
                //this takes care of it at an image level, either flagging it to be hidden, which applies a
                //custom spoiler image, or removes the image from the post entirely since this is a Post.Builder instance
                if (filter.action == FilterAction.HIDE.id) {
                    image.hidden = true;
                } else if (filter.action == FilterAction.REMOVE.id) {
                    post.images.remove(image);
                }
                return ChanSettings.applyImageFilterToPost.get();
            }
        }

        //figure out if the post has a flag code, if so check the filter
        String flagCode = "";
        if (post.httpIcons != null) {
            for (PostHttpIcon icon : post.httpIcons) {
                if (icon.type == SiteEndpoints.ICON_TYPE.COUNTRY_FLAG) {
                    flagCode = icon.code;
                    break;
                }
                if (icon.type == SiteEndpoints.ICON_TYPE.BOARD_FLAG) {
                    flagCode = icon.code;
                    break;
                }
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

    @AnyThread
    public boolean matches(Filter filter, FilterType type, CharSequence text, boolean forceCompile) {
        if ((filter.type & type.flag) == 0) return false;
        if (text == null) return false;

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
            if (matcher.find()) {
                MatchResult result = matcher.toMatchResult();
                if (text instanceof Spannable && ChanSettings.debugFilters.get()) {
                    ((Spannable) text).setSpan(
                            new FilterHighlightSpan(ThemeHelper.getTheme()),
                            result.start(),
                            result.end(),
                            Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    );
                    final String filterPattern = filter.pattern;
                    ((Spannable) text).setSpan(new ClickableSpan() {
                        @Override
                        public void onClick(@NonNull View widget) {
                            showToast(getAppContext(), "Matching filter: " + filterPattern, Toast.LENGTH_LONG);
                        }

                        @Override
                        public void updateDrawState(@NonNull TextPaint ds) {
                            ds.setUnderlineText(true);
                        }
                    }, result.start(), result.end(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                }
                return true;
            } else {
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
                //Don't allow an empty regex string (would match everything)
                pattern = isRegex.group(1).length() > 0 ? Pattern.compile(isRegex.group(1), flags) : null;
            } catch (PatternSyntaxException e) {
                return null;
            }
        } else if (rawPattern.length() >= 2 && rawPattern.charAt(0) == '"'
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
                text.append("(\\b")
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

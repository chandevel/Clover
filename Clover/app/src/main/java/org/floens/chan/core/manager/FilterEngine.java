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

import org.floens.chan.core.model.Filter;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FilterEngine {
    private static final String TAG = "FilterEngine";

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
            for (FilterType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
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
            for (FilterAction type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    private static final FilterEngine instance = new FilterEngine();

    public static FilterEngine getInstance() {
        return instance;
    }

    private List<Filter> filters = new ArrayList<>();

    public FilterEngine() {

    }

    public void add(Filter filter) {
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

    public boolean matches(Filter filter, String text) {
        FilterType type = FilterType.forId(filter.type);
        if (type.isRegex) {
            Pattern compiled = filter.compiledPattern;
            if (compiled == null) {
                compiled = filter.compiledPattern = compile(filter.pattern);
                Logger.test("Resulting pattern: " + filter.compiledPattern);
            }

            if (compiled != null) {
                return compiled.matcher(text).find();
            } else {
                Logger.e(TAG, "Invalid pattern");
                return false;
            }
        } else {
            return text.equals(filter.pattern);
        }
    }

    private String escapeRegex(String filthy) {
        return filterFilthyPattern.matcher(filthy).replaceAll("\\\\$1"); // Escape regex special characters with a \
    }
}

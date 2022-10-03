package com.github.adamantcheese.chan.utils;

import static com.github.adamantcheese.chan.utils.StringUtils.RenderOrder.RENDER_NORMAL;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.*;
import android.text.format.DateUtils;
import android.text.style.CharacterStyle;
import android.util.Base64;

import androidx.annotation.*;

import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.ui.text.CenteringImageSpan;
import com.github.adamantcheese.chan.ui.text.SearchHighlightSpan;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.google.common.io.Files;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;
import okio.ByteString;

public class StringUtils {
    @SuppressWarnings("RegExpRedundantEscape")
    private static final String RESERVED_CHARACTERS = "|?*<\":>+\\[\\]/'\\\\\\s";
    private static final String RESERVED_CHARACTERS_DIR = "[" + RESERVED_CHARACTERS + "." + "]";
    private static final String RESERVED_CHARACTERS_FILE = "[" + RESERVED_CHARACTERS + "]";

    public static DateFormat UTCFormat = SimpleDateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);

    static {
        UTCFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public static String dirNameRemoveBadCharacters(String dirName) {
        return dirName.replaceAll(" ", "_").replaceAll(RESERVED_CHARACTERS_DIR, "");
    }

    /**
     * The same as dirNameRemoveBadCharacters but allows dots since file names can have extensions
     */
    public static String fileNameRemoveBadCharacters(String filename) {
        return filename.replaceAll(" ", "_").replaceAll(RESERVED_CHARACTERS_FILE, "");
    }

    @Nullable
    public static String decodeBase64(String base64Encoded) {
        try {
            return new ByteString(Base64.decode(base64Encoded, Base64.DEFAULT)).hex();
        } catch (Throwable error) {
            Logger.e("decodeBase64", "Error decoding base64 string! Msg: " + error.getMessage());
            return null;
        }
    }

    public static String maskPostNo(int postNo) {
        String postNoString = String.valueOf(postNo);
        if (postNoString.length() >= 4) {
            return postNoString.substring(0, postNoString.length() - 3) + "XXX";
        }

        return postNoString;
    }

    public static String maskImageUrl(HttpUrl url) {
        if (url == null) return "";

        String result = url.toString();
        if (result.length() < 4) {
            return result;
        }

        String extension = Files.getFileExtension(result);

        int charactersToTrim = 4 + extension.length();

        if (result.length() < charactersToTrim) {
            return result;
        }

        String trimmedUrl = result.substring(0, result.length() - charactersToTrim);
        return trimmedUrl + "XXX." + extension;
    }

    public static boolean isAnyIgnoreCase(String s, String... strings) {
        if (s == null) return false;
        for (String str : strings) {
            if (s.equalsIgnoreCase(str)) {
                return true;
            }
        }
        return false;
    }

    public static boolean startsWithAny(String s, String... prefixes) {
        for (String prefix : prefixes) {
            if (s.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean endsWithAny(String s, String... suffixes) {
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean containsAny(CharSequence s, CharSequence... contains) {
        for (CharSequence contain : contains) {
            if (TextUtils.indexOf(s, contain) >= 0) {
                return true;
            }
        }
        return false;
    }

    public static String caseAndSpace(String input, String delimiter, boolean onlyFirst) {
        String[] parts;
        if (delimiter != null) {
            parts = input.split(delimiter);
        } else {
            parts = new String[1];
            parts[0] = input;
        }
        boolean first = true;
        StringBuilder properCaseString = new StringBuilder();
        for (String part : parts) {
            part = part.toLowerCase(Locale.ENGLISH);
            if (first) {
                part = part.substring(0, 1).toUpperCase(Locale.ENGLISH) + part.substring(1);
                first = !onlyFirst;
            }
            properCaseString.append(part).append(' ');
        }
        return properCaseString.deleteCharAt(properCaseString.length() - 1).toString();
    }

    public static String getCurrentDateAndTimeUTC() {
        return UTCFormat.format(new Date());
    }

    public static String getCurrentTimeDefaultLocale() {
        return DateFormat
                .getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault())
                .format(new Date());
    }

    public static String getTimeDefaultLocale(long unixTime) {
        return DateFormat
                .getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault())
                .format(new Date(unixTime));
    }

    public static String centerEllipsize(String string, int maxLength) {
        if (string.length() <= maxLength) {
            return string;
        }

        return string.substring(0, maxLength / 2 + (maxLength % 2 - 1)) + "\u2026" + string.substring(string.length()
                - maxLength / 2
                - maxLength % 2);
    }

    public static String getShortString(int value) {
        String count;
        if (value < 0) {
            count = "?";
        } else if (value < 1000) {
            count = String.valueOf(value);
        } else {
            int k = value / 1000;
            if (k < 10) {
                count = k + "k+";
            } else if (k < 100) {
                count = k + "k";
            } else {
                count = "XD";
            }
        }
        return count;
    }

    /**
     * @param color A ColorInt
     * @return RRGGBB, no alpha
     */
    public static String getRGBColorIntString(@ColorInt int color) {
        return String.format("%06X", 0xFFFFFF & color);
    }

    public static boolean containsIgnoreCase(@Nullable CharSequence source, @Nullable CharSequence needle) {
        if (source == null || needle == null) return false;
        return source.toString().toLowerCase(Locale.ENGLISH).contains(needle.toString().toLowerCase(Locale.ENGLISH));
    }

    private static final Pattern iso8601Time = Pattern.compile("PT?(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");

    public static String prettyPrint8601Time(String ISO8601Duration) {
        Matcher m = iso8601Time.matcher(ISO8601Duration);
        String ret;
        if (m.matches()) {
            int hours = 0, minutes = 0, seconds = 0;
            try {
                hours = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
            try {
                minutes = Integer.parseInt(m.group(2));
            } catch (Exception ignored) {}
            try {
                seconds = Integer.parseInt(m.group(3));
            } catch (Exception ignored) {}

            String secondString = (seconds < 10 ? "0" : "") + seconds;
            if (hours > 0) {
                String minuteString = (minutes < 10 ? "0" : "") + minutes;
                ret = hours + ":" + minuteString + ":" + secondString;
            } else {
                ret = minutes + ":" + secondString;
            }
        } else if ("P0D".equals(ISO8601Duration)) {
            ret = "LIVE";
        } else {
            ret = "??:??";
        }

        return "[" + ret + "]";
    }

    public static String prettyPrintDateUtilsElapsedTime(double elapsedSeconds) {
        if (Double.isNaN(elapsedSeconds) || Double.isInfinite(elapsedSeconds) || elapsedSeconds == 0.0) return "?:??";
        String out = DateUtils.formatElapsedTime(Math.round(elapsedSeconds));
        return "[" + ((out.charAt(0) == '0' && Character.isDigit(out.charAt(1))) ? out.substring(1) : out) + "]";
    }

    public static SpannableStringBuilder applySearchSpans(
            Theme theme, @Nullable CharSequence source, String searchQuery
    ) {
        SpannableStringBuilder sourceCopy = new SpannableStringBuilder(source == null ? "" : source);
        if (!TextUtils.isEmpty(searchQuery)) {
            Pattern search = Pattern.compile(FilterEngine.escapeRegex(searchQuery), Pattern.CASE_INSENSITIVE);
            Matcher searchMatch = search.matcher(sourceCopy);
            // apply new spans
            while (searchMatch.find()) {
                sourceCopy.setSpan(
                        new SearchHighlightSpan(theme),
                        searchMatch.toMatchResult().start(),
                        searchMatch.toMatchResult().end(),
                        makeSpanOptions(RENDER_NORMAL)
                );
            }
        }
        return sourceCopy;
    }

    // Copied from Apache Commons Lang 3
    @NonNull
    public static CharSequence chomp(@Nullable CharSequence str) {
        if (str == null || str.length() == 0) {
            return "";
        }

        if (str.length() == 1) {
            final char ch = str.charAt(0);
            if (ch == '\r' || ch == '\n') {
                return "";
            }
            return str;
        }

        int lastIdx = str.length() - 1;
        final char last = str.charAt(lastIdx);

        if (last == '\n') {
            if (str.charAt(lastIdx - 1) == '\r') {
                lastIdx--;
            }
        } else if (last != '\r') {
            lastIdx++;
        }
        return str.subSequence(0, lastIdx);
    }

    public enum RenderOrder {
        RENDER_ABOVE_ALL((byte) 1),
        RENDER_ABOVE_ELSE((byte) 95),
        RENDER_NORMAL((byte) 127),
        RENDER_BELOW_ELSE((byte) 159),
        RENDER_BELOW_ALL((byte) 255);

        public byte priority;

        RenderOrder(byte priority) {
            this.priority = priority;
        }
    }

    public static Spannable span(CharSequence text, Object... spans) {
        return spanWithPriority(text, RENDER_NORMAL, spans);
    }

    /**
     * @param text     The text to style.
     * @param priority The priority to make for span flags. Higher priorities render first, lower priorities render last.
     * @param spans    All the spans to apply to the text.
     * @return A styled string.
     */
    public static Spannable spanWithPriority(CharSequence text, RenderOrder priority, Object... spans) {
        Spannable ret;
        if (!(text instanceof Spannable)) {
            ret = new SpannableString(text);
        } else {
            ret = (Spannable) text;
        }
        if (spans == null || spans.length == 0)
            throw new IllegalArgumentException("Called spanWithPriority, but no spans!");
        for (Object span : spans) {
            if (span == null) continue;
            ret.setSpan(span, 0, text.length(), makeSpanOptions(priority));
        }
        return ret;
    }

    /**
     * @param order The order to make for span flags. Higher priorities render first, lower priorities render last.
     * @return The flags set up for setSpan.
     */
    public static int makeSpanOptions(RenderOrder order) {
        return (order.priority << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
                | Spanned.SPAN_INCLUSIVE_EXCLUSIVE;
    }

    public static void replaceSpan(Spannable text, CharacterStyle source, CharacterStyle replacement) {
        text.setSpan(replacement, text.getSpanStart(source), text.getSpanEnd(source), makeSpanOptions(RENDER_NORMAL));
        text.removeSpan(source);
    }

    public static CharSequence replaceAll(
            CharSequence source,
            TargetGenerator targetGenerator,
            ReplacementGenerator replacementGenerator,
            boolean inPlace
    ) {
        SpannableStringBuilder replaced;
        if (inPlace) {
            if (source instanceof SpannableStringBuilder) {
                replaced = (SpannableStringBuilder) source;
            } else {
                throw new IllegalArgumentException(
                        "If you want to replace all in-place, provide a SpannableStringBuilder as the source.");
            }
        } else {
            replaced = new SpannableStringBuilder(source);
        }
        int index = 0;
        while (true) {
            // generate an item to be replaced
            CharSequence target = targetGenerator.nextTarget();
            if (TextUtils.isEmpty(target)) break;

            // search from the last known replacement location
            index = TextUtils.indexOf(replaced, target, index);
            if (index < 0) break;

            // replace the proper section with the replacement
            CharSequence replacement = replacementGenerator.generateReplacement(target);
            replaced.replace(index, index + target.length(), replacement);

            // index forward past the replacement
            index = index + replacement.length();
        }
        return replaced;
    }

    public static CharSequence replaceAll(CharSequence source, CharSequence needle, CharSequence replacement) {
        return replaceAll(source, () -> needle, (source1) -> replacement, false);
    }

    public interface TargetGenerator {
        CharSequence nextTarget();
    }

    public interface ReplacementGenerator {
        CharSequence generateReplacement(CharSequence source);
    }

    public static class MatcherTargetGenerator
            implements TargetGenerator {
        private final Matcher matcher;

        public MatcherTargetGenerator(Pattern matcherFrom, CharSequence forText) {
            matcher = matcherFrom.matcher(forText);
        }

        @Override
        public CharSequence nextTarget() {
            if (matcher.find()) return matcher.group(0);
            return null;
        }
    }

    public static CharSequence prependIcon(
            @NonNull Context context, @NonNull CharSequence total, @NonNull Bitmap bitmap, float height
    ) {
        CenteringImageSpan imageSpan = new CenteringImageSpan(context, bitmap);
        int width = (int) (height / (bitmap.getHeight() / (float) bitmap.getWidth()));
        imageSpan.getDrawable().setBounds(0, 0, width, (int) height);
        return TextUtils.concat(span(" ", imageSpan), " ", total);
    }
}

package com.github.adamantcheese.chan.utils;

import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.vdurmont.emoji.EmojiParser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class StringUtils {
    private static final String TAG = "StringUtils";
    private static final Pattern IMAGE_THUMBNAIL_EXTRACTOR_PATTERN = Pattern.compile("/(\\d{12,32}+)s.(.*)");
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toLowerCase(Locale.ENGLISH).toCharArray();
    @SuppressWarnings("RegExpRedundantEscape")
    private static final String RESERVED_CHARACTERS = "|?*<\":>+\\[\\]/'\\\\\\s";
    private static final String RESERVED_CHARACTERS_DIR = "[" + RESERVED_CHARACTERS + "." + "]";
    private static final String RESERVED_CHARACTERS_FILE = "[" + RESERVED_CHARACTERS + "]";

    public static final DateTimeFormatter UTC_DATE_TIME_PRINTER =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withZone(ZoneId.of("UTC"));

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];

        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }

        return new String(hexChars);
    }

    @Nullable
    public static String convertThumbnailUrlToFilenameOnDisk(@Nullable HttpUrl url) {
        if (url == null) return null;
        Matcher matcher = IMAGE_THUMBNAIL_EXTRACTOR_PATTERN.matcher(url.toString());
        if (matcher.find()) {
            String filename = matcher.group(1);
            String extension = matcher.group(2);

            if (filename == null || extension == null) {
                return null;
            }

            if (filename.isEmpty() || extension.isEmpty()) {
                return null;
            }

            return String.format("%s_thumbnail.%s", filename, extension);
        }

        return null;
    }

    @Nullable
    public static String extractFileNameExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index == -1) {
            return null;
        }

        return filename.substring(index + 1);
    }

    @NonNull
    public static String removeExtensionFromFileName(String filename) {
        int index = filename.lastIndexOf('.');
        if (index == -1) {
            return filename;
        }

        return filename.substring(0, index);
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
        byte[] bytes;

        try {
            bytes = Base64.decode(base64Encoded, Base64.DEFAULT);
        } catch (Throwable error) {
            Logger.e(TAG, "Error decoding base64 string! Msg: " + error.getMessage());
            return null;
        }

        return bytesToHex(bytes);
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

        String extension = extractFileNameExtension(result);

        int extensionLength = extension == null ? 0 : (extension.length() + 1);
        int charactersToTrim = 3 + extensionLength;

        if (result.length() < charactersToTrim) {
            return result;
        }

        String trimmedUrl = result.substring(0, result.length() - charactersToTrim);
        return trimmedUrl + "XXX" + (extension == null ? "" : "." + extension);
    }

    public static boolean endsWithAny(String s, String[] suffixes) {
        for (String suffix : suffixes) {
            if (s.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public static String parseEmojiToAscii(String input) {
        return EmojiParser.parseFromUnicode(
                input,
                e -> ":" + e.getEmoji().getAliases().get(0) + (e.hasFitzpatrick() ? "|" + e.getFitzpatrickType() : "")
                        + ": "
        );
    }

    public static String caseAndSpace(String input, String delimiter) {
        String[] parts;
        if (delimiter != null) {
            parts = input.split(delimiter);
        } else {
            parts = new String[1];
            parts[0] = input;
        }
        StringBuilder properCaseString = new StringBuilder();
        for (String part : parts) {
            part = part.toLowerCase(Locale.ENGLISH);
            part = part.substring(0, 1).toUpperCase(Locale.ENGLISH) + part.substring(1);
            properCaseString.append(part).append(' ');
        }
        return properCaseString.deleteCharAt(properCaseString.length() - 1).toString();
    }

    public static String getCurrentDateAndTimeUTC() {
        return UTC_DATE_TIME_PRINTER.format(Instant.now());
    }

    public static String centerEllipsize(String string, int maxLength) {
        if (string.length() <= maxLength) {
            return string;
        }

        return string.substring(0, maxLength / 2 + (maxLength % 2 - 1)) + "\u2026" + string.substring(
                string.length() - maxLength / 2 - maxLength % 2);
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
}

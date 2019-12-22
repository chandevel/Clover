package com.github.adamantcheese.chan.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    private static final Pattern IMAGE_THUMBNAIL_EXTRACTOR_PATTERN = Pattern.compile("/(\\d{12,32}+)s.(.*)");

    @Nullable
    public static String convertThumbnailUrlToFilenameOnDisk(String url) {
        Matcher matcher = IMAGE_THUMBNAIL_EXTRACTOR_PATTERN.matcher(url);
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
        return dirName.replaceAll(" ", "_").replaceAll("[^a-zA-Z0-9_-]", "");
    }

    /**
     * The same as dirNameRemoveBadCharacters but allows dots since file names can have extensions
     */
    public static String fileNameRemoveBadCharacters(String filename) {
        return filename.replaceAll(" ", "_").replaceAll("[^a-zA-Z0-9_.-]", "");
    }
}

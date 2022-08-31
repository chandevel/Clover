package com.github.adamantcheese.chan.utils;

import com.github.adamantcheese.chan.BuildConfig;

import okhttp3.HttpUrl;

/**
 * A class to contain commonly used BuildConfig invocations
 */
public class BuildConfigUtils {
    public static final String VERSION = BuildConfig.APP_LABEL + "/" + BuildConfig.VERSION_NAME;
    public static final HttpUrl INTERNAL_SPOILER_THUMB_URL =
            HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");
    public static final HttpUrl AUDIO_THUMB_URL = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "audio_thumb.png");
    public static final HttpUrl SWF_THUMB_URL = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "swf_thumb.png");
    public static final HttpUrl DELETED_IMAGE_THUMB_URL =
            HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "file_deleted.png");
    public static final HttpUrl ARCHIVE_MISSING_THUMB_URL =
            HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "archive_missing.png");
    public static final HttpUrl HIDE_THUMB_URL = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "hide_thumb.png");
    public static final HttpUrl TEST_POST_IMAGE_URL = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "new_icon_512.png");
    public static final HttpUrl TEST_POST_ICON_URL = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "icon.png");
    public static final HttpUrl DEFAULT_SPOILER_IMAGE_URL =
            HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "default_spoiler.png");
}

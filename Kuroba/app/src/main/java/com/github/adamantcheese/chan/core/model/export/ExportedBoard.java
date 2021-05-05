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
package com.github.adamantcheese.chan.core.model.export;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;

public class ExportedBoard {
    @SerializedName("site_id")
    private final int siteId;
    @SerializedName("saved")
    private final boolean saved;
    @SerializedName("order")
    private final int order;
    @SerializedName("name")
    @Nullable
    private String name;
    @SerializedName("code")
    @Nullable
    private final String code;
    @SerializedName("work_safe")
    private final boolean workSafe;
    @SerializedName("per_page")
    private final int perPage;
    @SerializedName("pages")
    private final int pages;
    @SerializedName("max_file_size")
    private final int maxFileSize;
    @SerializedName("max_webm_size")
    private final int maxWebmSize;
    @SerializedName("max_comment_chars")
    private final int maxCommentChars;
    @SerializedName("bump_limit")
    private final int bumpLimit;
    @SerializedName("image_limit")
    private final int imageLimit;
    @SerializedName("cooldown_threads")
    private final int cooldownThreads;
    @SerializedName("cooldown_replies")
    private final int cooldownReplies;
    @SerializedName("cooldown_images")
    private final int cooldownImages;
    @SerializedName("spoilers")
    private final boolean spoilers;
    @SerializedName("custom_spoilers")
    private final int customSpoilers;
    @SerializedName("user_ids")
    private final boolean userIds;
    @SerializedName("code_tags")
    private final boolean codeTags;
    @SerializedName("preupload_captcha")
    private final boolean preuploadCaptcha;
    @SerializedName("country_flags")
    private final boolean countryFlags;
    @SerializedName("board_flags")
    private HashMap<String, String> boardFlags;
    @SerializedName("math_tags")
    private final boolean mathTags;
    @SerializedName("description")
    @Nullable
    private final String description;
    @SerializedName("archive")
    private final boolean archive;

    public ExportedBoard(
            int siteId,
            boolean saved,
            int order,
            @NonNull String name,
            @NonNull String code,
            boolean workSafe,
            int perPage,
            int pages,
            int maxFileSize,
            int maxWebmSize,
            int maxCommentChars,
            int bumpLimit,
            int imageLimit,
            int cooldownThreads,
            int cooldownReplies,
            int cooldownImages,
            boolean spoilers,
            int customSpoilers,
            boolean userIds,
            boolean codeTags,
            boolean preuploadCaptcha,
            boolean countryFlags,
            @NonNull HashMap<String, String> boardFlags,
            boolean mathTags,
            @NonNull String description,
            boolean archive
    ) {
        this.siteId = siteId;
        this.saved = saved;
        this.order = order;
        this.name = name;
        this.code = code;
        this.workSafe = workSafe;
        this.perPage = perPage;
        this.pages = pages;
        this.maxFileSize = maxFileSize;
        this.maxWebmSize = maxWebmSize;
        this.maxCommentChars = maxCommentChars;
        this.bumpLimit = bumpLimit;
        this.imageLimit = imageLimit;
        this.cooldownThreads = cooldownThreads;
        this.cooldownReplies = cooldownReplies;
        this.cooldownImages = cooldownImages;
        this.spoilers = spoilers;
        this.customSpoilers = customSpoilers;
        this.userIds = userIds;
        this.codeTags = codeTags;
        this.preuploadCaptcha = preuploadCaptcha;
        this.countryFlags = countryFlags;
        this.boardFlags = boardFlags;
        this.mathTags = mathTags;
        this.description = description;
        this.archive = archive;
    }

    public int getSiteId() {
        return siteId;
    }

    public boolean isSaved() {
        return saved;
    }

    public int getOrder() {
        return order;
    }

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public String getCode() {
        return code;
    }

    public boolean isWorkSafe() {
        return workSafe;
    }

    public int getPerPage() {
        return perPage;
    }

    public int getPages() {
        return pages;
    }

    public int getMaxFileSize() {
        return maxFileSize;
    }

    public int getMaxWebmSize() {
        return maxWebmSize;
    }

    public int getMaxCommentChars() {
        return maxCommentChars;
    }

    public int getBumpLimit() {
        return bumpLimit;
    }

    public int getImageLimit() {
        return imageLimit;
    }

    public int getCooldownThreads() {
        return cooldownThreads;
    }

    public int getCooldownReplies() {
        return cooldownReplies;
    }

    public int getCooldownImages() {
        return cooldownImages;
    }

    public boolean isSpoilers() {
        return spoilers;
    }

    public int getCustomSpoilers() {
        return customSpoilers;
    }

    public boolean isUserIds() {
        return userIds;
    }

    public boolean isCodeTags() {
        return codeTags;
    }

    public boolean isPreuploadCaptcha() {
        return preuploadCaptcha;
    }

    public boolean isCountryFlags() {
        return countryFlags;
    }

    public HashMap<String, String> getBoardFlags() {
        return boardFlags;
    }

    public void setBoardFlags(@NonNull HashMap<String, String> hashMap) {
        this.boardFlags = hashMap;
    }

    public boolean isMathTags() {
        return mathTags;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public boolean isArchive() {
        return archive;
    }
}

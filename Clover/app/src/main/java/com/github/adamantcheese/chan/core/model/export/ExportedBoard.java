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
package com.github.adamantcheese.chan.core.model.export;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class ExportedBoard {
    @SerializedName("site_id")
    private int siteId;
    @SerializedName("saved")
    private boolean saved;
    @SerializedName("order")
    private int order;
    @SerializedName("name")
    @Nullable
    private String name;
    @SerializedName("code")
    @Nullable
    private String code;
    @SerializedName("work_safe")
    private boolean workSafe;
    @SerializedName("per_page")
    private int perPage;
    @SerializedName("pages")
    private int pages;
    @SerializedName("max_file_size")
    private int maxFileSize;
    @SerializedName("max_webm_size")
    private int maxWebmSize;
    @SerializedName("max_comment_chars")
    private int maxCommentChars;
    @SerializedName("bump_limit")
    private int bumpLimit;
    @SerializedName("image_limit")
    private int imageLimit;
    @SerializedName("cooldown_threads")
    private int cooldownThreads;
    @SerializedName("cooldown_replies")
    private int cooldownReplies;
    @SerializedName("cooldown_images")
    private int cooldownImages;
    @SerializedName("cooldown_replies_intra")
    private int cooldownRepliesIntra;
    @SerializedName("cooldown_images_intra")
    private int cooldownImagesIntra;
    @SerializedName("spoilers")
    private boolean spoilers;
    @SerializedName("custom_spoilers")
    private int customSpoilers;
    @SerializedName("user_ids")
    private boolean userIds;
    @SerializedName("code_tags")
    private boolean codeTags;
    @SerializedName("preupload_captcha")
    private boolean preuploadCaptcha;
    @SerializedName("country_flags")
    private boolean countryFlags;
    @SerializedName("troll_flags")
    private boolean trollFlags;
    @SerializedName("math_tags")
    private boolean mathTags;
    @SerializedName("description")
    @Nullable
    private String description;
    @SerializedName("archive")
    private boolean archive;

    public ExportedBoard(
            int siteId,
            boolean saved,
            int order,
            @NonNull
            String name,
            @NonNull
            String code,
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
            int cooldownRepliesIntra,
            int cooldownImagesIntra,
            boolean spoilers,
            int customSpoilers,
            boolean userIds,
            boolean codeTags,
            boolean preuploadCaptcha,
            boolean countryFlags,
            boolean trollFlags,
            boolean mathTags,
            @NonNull
            String description,
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
        this.cooldownRepliesIntra = cooldownRepliesIntra;
        this.cooldownImagesIntra = cooldownImagesIntra;
        this.spoilers = spoilers;
        this.customSpoilers = customSpoilers;
        this.userIds = userIds;
        this.codeTags = codeTags;
        this.preuploadCaptcha = preuploadCaptcha;
        this.countryFlags = countryFlags;
        this.trollFlags = trollFlags;
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

    public int getCooldownRepliesIntra() {
        return cooldownRepliesIntra;
    }

    public int getCooldownImagesIntra() {
        return cooldownImagesIntra;
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

    public boolean isTrollFlags() {
        return trollFlags;
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

/*
 * Clover4 - *chan browser https://github.com/Adamantcheese/Clover4/
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
package com.github.adamantcheese.chan.core.model.orm;

import android.support.v4.util.ObjectsCompat;
import android.text.TextUtils;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import com.github.adamantcheese.chan.core.site.Site;

/**
 * A board is something that can be browsed, it is unique by it's site and code.
 */
@DatabaseTable(tableName = "board")
public class Board implements Cloneable {
    @DatabaseField(generatedId = true)
    public int id;

    @DatabaseField(columnName = "site", index = true, indexName = "board_site_idx")
    public int siteId;

    /**
     * The site this board belongs to, loaded with {@link #siteId} in the database manager.
     */
    public transient Site site;

    /**
     * The board appears in the dropdown.
     */
    @DatabaseField(index = true, indexName = "board_saved_idx")
    public boolean saved = false;

    /**
     * Order of the board in the dropdown, user-set.
     */
    @DatabaseField
    public int order;

    @DatabaseField(columnName = "key") // named key for legacy support
    public String name;

    // named value for legacy support
    @DatabaseField(columnName = "value", index = true, indexName = "board_value_idx")
    // TODO(sec) force filter this to ascii & numbers.
    public String code;

    @DatabaseField
    public boolean workSafe = false;

    @DatabaseField
    public int perPage = 15;

    @DatabaseField
    public int pages = 10;

    @DatabaseField
    public int maxFileSize = -1;

    @DatabaseField
    public int maxWebmSize = -1;

    @DatabaseField
    public int maxCommentChars = -1;

    @DatabaseField
    public int bumpLimit = -1;

    @DatabaseField
    public int imageLimit = -1;

    @DatabaseField
    public int cooldownThreads = 0;

    @DatabaseField
    public int cooldownReplies = 0;

    @DatabaseField
    public int cooldownImages = 0;

    // unused, to be removed
    @Deprecated
    @DatabaseField
    public int cooldownRepliesIntra = -1;

    // unused, to be removed
    @Deprecated
    @DatabaseField
    public int cooldownImagesIntra = -1;

    @DatabaseField
    public boolean spoilers = false;

    @DatabaseField
    public int customSpoilers = -1;

    @DatabaseField
    public boolean userIds = false;

    @DatabaseField
    public boolean codeTags = false;

    @DatabaseField
    public boolean preuploadCaptcha = false;

    @DatabaseField
    public boolean countryFlags = false;

    @Deprecated
    @DatabaseField
    public boolean trollFlags = false;

    @DatabaseField
    public boolean mathTags = false;

    @DatabaseField
    public String description;

    @DatabaseField
    public boolean archive = false;

    @Deprecated // public, at least
    public Board() {
    }

    public Board(
            int siteId,
            boolean saved,
            int order,
            String name,
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

    /**
     * UNSAFE TO USE!!! FOR TESTS ONLY!!!
     * <p>
     * Site class accesses Android's context so it cannot be used in Unit tests.
     * That's why this constructor was added.
     */
    @Deprecated
    public Board(String name, String code) {
        this.name = name;
        this.code = code;
    }

    @Deprecated
    public Board(Site site, String name, String code, boolean saved, boolean workSafe) {
        this.siteId = site.id();
        this.site = site;
        this.name = name;
        this.code = code;
        this.saved = saved;
        this.workSafe = workSafe;
    }

    public static Board fromSiteNameCode(Site site, String name, String code) {
        Board board = new Board();
        board.siteId = site.id();
        board.site = site;
        board.name = name;
        board.code = code;
        return board;
    }

    public boolean finish() {
        return !TextUtils.isEmpty(name) && !TextUtils.isEmpty(code) && perPage >= 0 && pages >= 0;
    }

    public Site getSite() {
        return site;
    }

    public boolean siteCodeEquals(Board other) {
        return code.equals(other.code) && other.siteId == siteId;
    }

    /**
     * Updates the board with data from {@code o}.<br>
     * {@link #id}, {@link #saved}, {@link #order} are skipped because these are user-set.
     *
     * @param o other board to update from.
     */
    public void updateExcludingUserFields(Board o) {
        siteId = o.siteId;
        site = o.site;
        name = o.name;
        code = o.code;
        workSafe = o.workSafe;
        perPage = o.perPage;
        pages = o.pages;
        maxFileSize = o.maxFileSize;
        maxWebmSize = o.maxWebmSize;
        maxCommentChars = o.maxCommentChars;
        bumpLimit = o.bumpLimit;
        imageLimit = o.imageLimit;
        cooldownThreads = o.cooldownThreads;
        cooldownReplies = o.cooldownReplies;
        cooldownImages = o.cooldownImages;
        cooldownRepliesIntra = o.cooldownRepliesIntra;
        cooldownImagesIntra = o.cooldownImagesIntra;
        spoilers = o.spoilers;
        customSpoilers = o.customSpoilers;
        userIds = o.userIds;
        codeTags = o.codeTags;
        preuploadCaptcha = o.preuploadCaptcha;
        countryFlags = o.countryFlags;
        trollFlags = o.trollFlags;
        mathTags = o.mathTags;
        description = o.description;
        archive = o.archive;
    }

    /**
     * Clones this board.
     *
     * @return copy of this board.
     */
    public Board clone() {
        Board b = new Board();
        b.id = id;
        b.siteId = siteId;
        b.site = site;
        b.name = name;
        b.code = code;
        b.saved = saved;
        b.order = order;
        b.workSafe = workSafe;
        b.perPage = perPage;
        b.pages = pages;
        b.maxFileSize = maxFileSize;
        b.maxWebmSize = maxWebmSize;
        b.maxCommentChars = maxCommentChars;
        b.bumpLimit = bumpLimit;
        b.imageLimit = imageLimit;
        b.cooldownThreads = cooldownThreads;
        b.cooldownReplies = cooldownReplies;
        b.cooldownImages = cooldownImages;
        b.cooldownRepliesIntra = cooldownRepliesIntra;
        b.cooldownImagesIntra = cooldownImagesIntra;
        b.spoilers = spoilers;
        b.customSpoilers = customSpoilers;
        b.userIds = userIds;
        b.codeTags = codeTags;
        b.preuploadCaptcha = preuploadCaptcha;
        b.countryFlags = countryFlags;
        b.trollFlags = trollFlags;
        b.mathTags = mathTags;
        b.description = description;
        b.archive = archive;
        return b;
    }

    public boolean equals(Object board) {
        if (board != null && board.getClass() == Board.class) {
            Board b = (Board) board;
            return name.equals(b.name) &&
                    code.equals(b.code) &&
                    workSafe == b.workSafe &&
                    perPage == b.perPage &&
                    pages == b.pages &&
                    maxFileSize == b.maxFileSize &&
                    maxWebmSize == b.maxWebmSize &&
                    maxCommentChars == b.maxCommentChars &&
                    bumpLimit == b.bumpLimit &&
                    imageLimit == b.imageLimit &&
                    cooldownThreads == b.cooldownThreads &&
                    cooldownReplies == b.cooldownReplies &&
                    cooldownImages == b.cooldownImages &&
                    cooldownRepliesIntra == b.cooldownRepliesIntra &&
                    cooldownImagesIntra == b.cooldownImagesIntra &&
                    spoilers == b.spoilers &&
                    customSpoilers == b.customSpoilers &&
                    userIds == b.userIds &&
                    codeTags == b.codeTags &&
                    preuploadCaptcha == b.preuploadCaptcha &&
                    countryFlags == b.countryFlags &&
                    trollFlags == b.trollFlags &&
                    mathTags == b.mathTags &&
                    ObjectsCompat.equals(description, b.description) &&
                    archive == b.archive;
        }
        return false;
    }
}

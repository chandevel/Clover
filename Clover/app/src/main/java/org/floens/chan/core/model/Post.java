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
package org.floens.chan.core.model;

import android.text.SpannableString;
import android.text.TextUtils;

import org.floens.chan.Chan;
import org.floens.chan.chan.ChanParser;
import org.floens.chan.chan.ChanUrls;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Contains all data needed to represent a single post.<br>
 * Call {@link #finish()} to parse the comment etc. The post data is invalid if finish returns false.<br>
 * This class has members that are threadsafe and some that are not, see the source for more info.
 */
public class Post {
    private static final Random random = new Random();

    // *** These next members don't get changed after finish() is called. Effectively final. ***
    public String board;

    public int no = -1;

    public int resto = -1;

    public boolean isOP = false;

    public String date;

    public String name = "";

    public CharSequence comment = "";

    public String subject = "";

    public long tim = -1;

    public String ext;

    public String filename;

    public int imageWidth;

    public int imageHeight;

    public boolean hasImage = false;

    public PostImage image;

    public String thumbnailUrl;

    public String imageUrl;

    public String tripcode = "";

    public String id = "";

    public String capcode = "";

    public String country = "";

    public String countryName = "";

    public long time = -1;

    public long fileSize;

    public String rawComment;

    public String countryUrl;

    public boolean spoiler = false;

    public boolean isSavedReply = false;

    public int filterHighlightedColor = 0;

    public boolean filterStub = false;

    public boolean filterRemove = false;


    /**
     * This post replies to the these ids. Is an unmodifiable list after finish().
     */
    public List<Integer> repliesTo = new ArrayList<>();

    public final ArrayList<PostLinkable> linkables = new ArrayList<>();

    public boolean parsedSpans = false;

    public SpannableString subjectSpan;

    public SpannableString nameSpan;

    public SpannableString tripcodeSpan;

    public SpannableString idSpan;

    public SpannableString capcodeSpan;

    public CharSequence nameTripcodeIdCapcodeSpan;

    // *** These next members may only change on the main thread after finish(). ***
    public boolean sticky = false;
    public boolean closed = false;
    public boolean archived = false;
    public int replies = -1;
    public int images = -1;
    public int uniqueIps = 1;
    public String title = "";

    // *** Threadsafe members, may be read and modified on any thread. ***
    public AtomicBoolean deleted = new AtomicBoolean(false);

    // *** Manual synchronization needed. ***
    /**
     * These ids replied to this post.<br>
     * <b>synchronize on this when accessing.</b>
     */
    public final List<Integer> repliesFrom = new ArrayList<>();

    /**
     * Finish up the data: parse the comment, check if the data is valid etc.
     *
     * @return false if this data is invalid
     */
    public boolean finish() {
        if (board == null)
            return false;

        if (no < 0 || resto < 0 || date == null || time < 0)
            return false;

        isOP = resto == 0;

        if (isOP && (replies < 0 || images < 0))
            return false;

        if (filename != null && ext != null && imageWidth > 0 && imageHeight > 0 && tim >= 0) {
            hasImage = true;
            imageUrl = ChanUrls.getImageUrl(board, Long.toString(tim), ext);
            filename = Parser.unescapeEntities(filename, false);

            if (spoiler) {
                Board b = Chan.getBoardManager().getBoardByCode(board);
                if (b != null && b.customSpoilers >= 0) {
                    thumbnailUrl = ChanUrls.getCustomSpoilerUrl(board, random.nextInt(b.customSpoilers) + 1);
                } else {
                    thumbnailUrl = ChanUrls.getSpoilerUrl();
                }
            } else {
                thumbnailUrl = ChanUrls.getThumbnailUrl(board, Long.toString(tim));
            }

            image = new PostImage(String.valueOf(tim), thumbnailUrl, imageUrl, filename, ext, imageWidth, imageHeight, spoiler, fileSize);
        }

        if (!TextUtils.isEmpty(country)) {
            countryUrl = ChanUrls.getCountryFlagUrl(country);
        }

        ChanParser.getInstance().parse(this);

        repliesTo = Collections.unmodifiableList(repliesTo);

        return true;
    }
}

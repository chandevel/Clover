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
package com.github.adamantcheese.chan.core.site.parser;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.LruCache;

import androidx.annotation.AnyThread;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.joda.time.Period;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParserHelper {
    private static final LinkExtractor LINK_EXTRACTOR =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    private static Pattern youtubeLinkPattern = Pattern.compile(
            "\\b\\w+://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})(.*)\\b");
    private static Bitmap youtubeIcon = BitmapFactory.decodeResource(AndroidUtils.getRes(), R.drawable.youtube_icon);
    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    public static LruCache<String, Pair<String, String>> youtubeCache = new LruCache<>(500);

    //@formatter:off
    private static Pattern imageUrlPattern = Pattern.compile("https?://.*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)", Pattern.CASE_INSENSITIVE);
    private static String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac"};
    //@formatter:on

    private static final Pattern dubsPattern = Pattern.compile("(\\d)\\1$");
    private static final Pattern tripsPattern = Pattern.compile("(\\d)\\1{2}$");
    private static final Pattern quadsPattern = Pattern.compile("(\\d)\\1{3}$");
    private static final Pattern quintsPattern = Pattern.compile("(\\d)\\1{4}$");
    private static final Pattern hexesPattern = Pattern.compile("(\\d)\\1{5}$");
    private static final Pattern septsPattern = Pattern.compile("(\\d)\\1{6}$");
    private static final Pattern octsPattern = Pattern.compile("(\\d)\\1{7}$");
    private static final Pattern nonsPattern = Pattern.compile("(\\d)\\1{8}$");
    private static final Pattern decsPattern = Pattern.compile("(\\d)\\1{9}$");

    /**
     * Detect links in the given spannable, and create PostLinkables with Type.LINK for the
     * links found onto the spannable.
     * <p>
     * The links are detected with the autolink-java library.
     *
     * @param theme     The theme to style the links with
     * @param post      The post where the linkables get added to.
     * @param text      Text to find links in
     * @param spannable Spannable to set the spans on.
     */
    public static void detectLinks(Theme theme, Post.Builder post, String text, SpannableString spannable) {
        final Iterable<LinkSpan> links = LINK_EXTRACTOR.extractLinks(text);
        for (final LinkSpan link : links) {
            final String linkText = text.substring(link.getBeginIndex(), link.getEndIndex());
            final PostLinkable pl = new PostLinkable(theme, linkText, linkText, PostLinkable.Type.LINK);
            //priority is 0 by default which is maximum above all else; higher priority is like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
            //we use 500 here for to go below post linkables, but above everything else basically
            spannable.setSpan(
                    pl,
                    link.getBeginIndex(),
                    link.getEndIndex(),
                    (500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
            );
            post.addLinkable(pl);
        }
    }

    public static SpannableString replaceYoutubeLinks(Theme theme, Post.Builder post, String text) {
        Map<String, String> titleURLMap =
                new HashMap<>(); //this map is inverted i.e. the title maps to the URL rather than the other way around
        StringBuffer newString = new StringBuffer();
        //find and replace all youtube URLs with their titles, but keep track in the map above for spans later
        Matcher linkMatcher = youtubeLinkPattern.matcher(text);
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            String videoID = linkMatcher.group(1);
            Pair<String, String> result = youtubeCache.get(URL);
            if (result == null) {
                result = NetUtils.makeJsonRequestSync(
                        //@formatter:off
                    HttpUrl.get("https://www.googleapis.com/youtube/v3/videos?part=snippet"
                            + (ChanSettings.parseYoutubeDuration.get() ? "%2CcontentDetails" : "")
                            + "&id=" + videoID
                            + "&fields=items%28id%2Csnippet%28title%29"
                            + (ChanSettings.parseYoutubeDuration.get() ? "%2CcontentDetails%28duration%29" : "")
                            + "%29&key=" + ChanSettings.parseYoutubeAPIKey.get()),
                    //@formatter:on
                        reader -> {
                        /*
                            {
                              "items": [
                                {
                                  "id": "UyXlt9PP4eM",
                                  "snippet": {
                                    "title": "ATC Spindle Part 3: Designing the Spindle Mount"
                                  },
                                    "contentDetails": {
                                      "duration": "PT22M27S"
                                    }
                                }
                              ]
                            }
                         */
                            reader.beginObject();
                            reader.nextName();
                            reader.beginArray();
                            reader.beginObject();
                            reader.nextName(); // video ID
                            reader.nextString();
                            reader.nextName(); // snippet
                            reader.beginObject();
                            reader.nextName(); // title
                            String title = reader.nextString();
                            Pair<String, String> ret = new Pair<>(title, null);
                            reader.endObject();
                            if (ChanSettings.parseYoutubeDuration.get()) {
                                reader.nextName(); // content details
                                reader.beginObject();
                                reader.nextName(); // duration
                                String duration = reader.nextString();
                                duration = StringUtils.getHourMinSecondString(Period.parse(duration));
                                ret = new Pair<>(title, duration);
                                reader.endObject();
                            }
                            reader.endObject();
                            reader.endArray();
                            reader.endObject();
                            return ret;
                        }
                );
                youtubeCache.put(URL, result);
            }
            //prepend two spaces for the youtube icon later
            String title = result != null ? result.first : URL;
            String duration = result != null ? result.second : null;
            String extraDur = duration != null ? " " + duration : "";
            titleURLMap.put("  " + title + extraDur, URL);
            linkMatcher.appendReplacement(newString, "  " + Matcher.quoteReplacement(title + extraDur));
        }
        linkMatcher.appendTail(newString);

        //we have a new string here with all the links replaced by their text equivalents, we need to add the linkables now using the map
        //we reference newString internally because SpannableString instances don't have an indexOf method, but the two are otherwise the same
        SpannableString finalizedString = new SpannableString(newString);
        for (String key : titleURLMap.keySet()) {
            //set the linkable to be the entire length, including the icon
            PostLinkable pl = new PostLinkable(theme, key, titleURLMap.get(key), PostLinkable.Type.LINK);
            finalizedString.setSpan(
                    pl,
                    newString.indexOf(key),
                    newString.indexOf(key) + key.length(),
                    (500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
            );
            post.addLinkable(pl);

            //set the youtube icon span for the linkable
            ImageSpan ytIcon = new ImageSpan(getAppContext(), youtubeIcon);
            int height = Integer.parseInt(ChanSettings.fontSize.get());
            int width = (int) (sp(height) / (youtubeIcon.getHeight() / (float) youtubeIcon.getWidth()));
            ytIcon.getDrawable().setBounds(0, 0, width, sp(height));
            finalizedString.setSpan(
                    ytIcon,
                    newString.indexOf(key),
                    newString.indexOf(key) + 1,
                    (500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY
            );
        }

        return finalizedString;
    }

    public static void addPostImages(Post.Builder post) {
        if (ChanSettings.parsePostImageLinks.get()) {
            for (PostLinkable linkable : post.getLinkables()) {
                if (post.images != null && post.images.size() >= 5) return; //max 5 images hotlinked
                if (linkable.type == PostLinkable.Type.LINK) {
                    Matcher matcher = imageUrlPattern.matcher(((String) linkable.value));
                    if (matcher.matches()) {
                        boolean noThumbnail = StringUtils.endsWithAny((String) linkable.value, noThumbLinkSuffixes);
                        String spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png";

                        HttpUrl imageUrl = HttpUrl.parse((String) linkable.value);
                        if (imageUrl == null) {
                            continue;
                        }

                        post.images(Collections.singletonList(new PostImage.Builder().serverFilename(matcher.group(1))
                                //spoiler thumb for some linked items, the image itself for the rest; probably not a great idea
                                .thumbnailUrl(HttpUrl.parse(noThumbnail ? spoilerThumbnail : (String) linkable.value))
                                .spoilerThumbnailUrl(HttpUrl.parse(spoilerThumbnail))
                                .imageUrl(imageUrl)
                                .filename(matcher.group(1))
                                .extension(matcher.group(2))
                                .spoiler(true)
                                .isInlined(true)
                                .size(-1)
                                .build()));
                    }
                }
            }
        }
    }

    public static String getRepeatDigits(int no) {
        String number = String.valueOf(no);
        //inverted order to match largest to smallest, otherwise will always match smallest
        if (decsPattern.matcher(number).find()) return "Decs";
        if (nonsPattern.matcher(number).find()) return "Nons";
        if (octsPattern.matcher(number).find()) return "Octs";
        if (septsPattern.matcher(number).find()) return "Septs";
        if (hexesPattern.matcher(number).find()) return "Sexes";
        if (quintsPattern.matcher(number).find()) return "Quints";
        if (quadsPattern.matcher(number).find()) return "Quads";
        if (tripsPattern.matcher(number).find()) return "Trips";
        if (dubsPattern.matcher(number).find()) return "Dubs";
        return null;
    }
}

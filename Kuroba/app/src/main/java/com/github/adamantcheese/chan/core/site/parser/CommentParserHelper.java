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

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.RequestFuture;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.json.JSONObject;
import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.Chan.instance;
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
    public static LruCache<String, String> youtubeTitleCache = new LruCache<>(500);
    public static LruCache<String, String> youtubeDurCache = new LruCache<>(500);

    //@formatter:off
    private static Pattern imageUrlPattern = Pattern.compile(".*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp)", Pattern.CASE_INSENSITIVE);
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
            spannable.setSpan(pl,
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
            String videoID = linkMatcher.group(1);
            RequestFuture<JSONObject> future = RequestFuture.newFuture();
            //this must be a GET request, so the jsonRequest object is null per documentation
            JsonObjectRequest request = new JsonObjectRequest(
                    "https://www.googleapis.com/youtube/v3/videos?part=snippet"
                            + (ChanSettings.parseYoutubeDuration.get() ? "%2CcontentDetails" : "") + "&id=" + videoID
                            + "&fields=items%28id%2Csnippet%28title%29" + (ChanSettings.parseYoutubeDuration.get()
                            ? "%2CcontentDetails%28duration%29"
                            : "") + "%29&key=" + ChanSettings.parseYoutubeAPIKey.get(),
                    null,
                    future,
                    future
            );
            instance(RequestQueue.class).add(request);

            String URL = linkMatcher.group(0);
            String title = youtubeTitleCache.get(URL);
            String duration = youtubeDurCache.get(URL);
            if (title == null || duration == null) {
                try {
                    // this will block so we get the title immediately
                    JSONObject response = future.get(2500, TimeUnit.MILLISECONDS);
                    title = response.getJSONArray("items")
                            .getJSONObject(0)
                            .getJSONObject("snippet")
                            .getString("title"); //the response is well formatted so this will always work
                    youtubeTitleCache.put(URL, title);
                    if (ChanSettings.parseYoutubeDuration.get()) {
                        duration = response.getJSONArray("items")
                                .getJSONObject(0)
                                .getJSONObject("contentDetails")
                                .getString("duration"); //the response is well formatted so this will always work
                        Period time = Period.parse(duration);
                        //format m?m:ss; ? is optional
                        //alternate h?h:mm:ss if hours
                        PeriodFormatter formatter = new PeriodFormatterBuilder().appendLiteral("[")
                                .minimumPrintedDigits(0) //don't print hours if none
                                .appendHours()
                                .appendSuffix(":")
                                .minimumPrintedDigits(time.getHours() > 0 ? 2 : 1) //two digit minutes if hours
                                .printZeroAlways() //always print 0 for minutes, if seconds only
                                .appendMinutes()
                                .appendSuffix(":")
                                .minimumPrintedDigits(2) //always print two digit seconds
                                .appendSeconds()
                                .appendLiteral("]")
                                .toFormatter();
                        duration = formatter.print(time);
                        youtubeDurCache.put(URL, duration);
                    }
                } catch (Exception e) {
                    //fall back to just showing the URL, otherwise it will display "null" which is pretty useless
                    title = URL;
                    duration = null;
                }
            }
            //prepend two spaces for the youtube icon later
            String extraDur = ChanSettings.parseYoutubeDuration.get() ? (duration != null ? " " + duration : "") : "";
            titleURLMap.put("  " + title + extraDur, URL);
            linkMatcher.appendReplacement(newString, "  " + title + extraDur);
        }
        linkMatcher.appendTail(newString);

        //we have a new string here with all the links replaced by their text equivalents, we need to add the linkables now using the map
        //we reference newString internally because SpannableString instances don't have an indexOf method, but the two are otherwise the same
        SpannableString finalizedString = new SpannableString(newString);
        for (String key : titleURLMap.keySet()) {
            //set the linkable to be the entire length, including the icon
            PostLinkable pl = new PostLinkable(theme, key, titleURLMap.get(key), PostLinkable.Type.LINK);
            finalizedString.setSpan(pl,
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
            finalizedString.setSpan(ytIcon,
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
                        boolean noThumbnail =
                                ((String) linkable.value).endsWith("webm") || ((String) linkable.value).endsWith("pdf")
                                        || ((String) linkable.value).endsWith("mp4");
                        String spoilerThumbnail =
                                "https://raw.githubusercontent.com/Adamantcheese/Kuroba/multi-feature/docs/internal_spoiler.png";
                        post.images(Collections.singletonList(new PostImage.Builder().serverFilename(matcher.group(1))
                                //spoiler thumb for some linked items, the image itself for the rest; probably not a great idea
                                .thumbnailUrl(HttpUrl.parse(noThumbnail ? spoilerThumbnail : (String) linkable.value))
                                .spoilerThumbnailUrl(HttpUrl.parse(spoilerThumbnail))
                                .imageUrl(HttpUrl.parse((String) linkable.value))
                                .filename(matcher.group(1))
                                .extension(matcher.group(2))
                                .spoiler(true)
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

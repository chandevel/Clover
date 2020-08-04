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

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.util.LruCache;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Headers;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParserHelper {
    private static final LinkExtractor LINK_EXTRACTOR =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    private static final Pattern YOUTUBE_LINK_PATTERN = Pattern.compile(
            "\\b\\w+://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})(.*)\\b");
    private static final Pattern iso8601Time = Pattern.compile("PT((\\d+)H)?((\\d+)M)?((\\d+)S)?");
    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    public static LruCache<String, Pair<String, String>> youtubeCache = new LruCache<>(500);

    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac"};

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
    public static void detectLinks(Theme theme, Post.Builder post, String text, SpannableStringBuilder spannable) {
        final Iterable<LinkSpan> links = LINK_EXTRACTOR.extractLinks(text);
        for (final LinkSpan link : links) {
            final String linkText = text.substring(link.getBeginIndex(), link.getEndIndex());
            //if this URL is a youtube link and we're parsing those, skip it, it'll be taken care of later
            if (ChanSettings.parseYoutubeTitles.get() && YOUTUBE_LINK_PATTERN.matcher(linkText).matches()) continue;
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

    public static void addPostImages(Post.Builder post) {
        if (ChanSettings.parsePostImageLinks.get()) {
            for (PostLinkable linkable : post.getLinkables()) {
                if (post.images != null && post.images.size() >= 5) return; //max 5 images hotlinked
                if (linkable.type == PostLinkable.Type.LINK) {
                    Matcher matcher = IMAGE_URL_PATTERN.matcher((String) linkable.value);
                    if (matcher.matches()) {
                        boolean noThumbnail = StringUtils.endsWithAny((String) linkable.value, noThumbLinkSuffixes);
                        String spoilerThumbnail = BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png";

                        HttpUrl imageUrl = HttpUrl.parse((String) linkable.value);
                        if (imageUrl == null) {
                            continue;
                        }

                        PostImage inlinedImage = new PostImage.Builder().serverFilename(matcher.group(1))
                                //spoiler thumb for some linked items, the image itself for the rest; probably not a great idea
                                .thumbnailUrl(HttpUrl.parse(noThumbnail ? spoilerThumbnail : (String) linkable.value))
                                .spoilerThumbnailUrl(HttpUrl.parse(spoilerThumbnail))
                                .imageUrl(imageUrl)
                                .filename(matcher.group(1))
                                .extension(matcher.group(2))
                                .spoiler(true)
                                .isInlined(true)
                                .size(-1)
                                .build();

                        post.images(Collections.singletonList(inlinedImage));

                        NetUtils.makeHeadersRequest(imageUrl, new NetUtils.HeaderResult() {
                            @Override
                            public void onHeaderFailure(Exception e) {}

                            @Override
                            public void onHeaderSuccess(Headers result) {
                                String size = result.get("Content-Length");
                                inlinedImage.size = size == null ? 0 : Long.parseLong(size);
                            }
                        });
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

    //for testing, using 4chanx's api key
    //normal https://www.googleapis.com/youtube/v3/videos?part=snippet&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE
    //duration https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%2CcontentDetails%28duration%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE

    /* SAMPLE JSON FOR DURATION (skip the contentDetails stuff for without)
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

    public static List<Call> replaceYoutubeLinks(Theme theme, Post post, @Nullable TextView toInvalidate) {
        List<Call> calls = new ArrayList<>();
        //find and replace all youtube URLs with their titles, but keep track in the map above for spans later
        Matcher linkMatcher = YOUTUBE_LINK_PATTERN.matcher(post.comment);
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);

            //@formatter:off
            HttpUrl requestUrl = HttpUrl.get("https://www.googleapis.com/youtube/v3/videos?part=snippet"
                    + (ChanSettings.parseYoutubeDuration.get() ? "%2CcontentDetails" : "")
                    + "&id=" + linkMatcher.group(1)
                    + "&fields=items%28id%2Csnippet%28title%29"
                    + (ChanSettings.parseYoutubeDuration.get() ? "%2CcontentDetails%28duration%29" : "")
                    + "%29&key=" + ChanSettings.parseYoutubeAPIKey.get());
            //@formatter:on

            boolean needsRequest = false;
            Pair<String, String> result = youtubeCache.get(URL);
            if (result != null) {
                if (result.second == null && ChanSettings.parseYoutubeDuration.get()) {
                    // remove the entry; it needs additional info now
                    youtubeCache.remove(URL);
                    needsRequest = true;
                }
            } else {
                needsRequest = true;
            }

            if (!needsRequest) {
                // we've previously cached this youtube title/duration and we don't need additional information
                performLinkReplacement(theme, post, result, URL, toInvalidate);
            } else {
                // we haven't cached this youtube title/duration, or we need additional information
                calls.add(NetUtils.makeJsonRequest(requestUrl, new NetUtils.JsonResult<Pair<String, String>>() {
                    @Override
                    public void onJsonFailure(Exception e) {
                        //failed to get, replace with just the URL and append the youtube icon
                        performLinkReplacement(theme, post, new Pair<>(URL, null), URL, toInvalidate);
                    }

                    @Override
                    public void onJsonSuccess(Pair<String, String> result) {
                        //got a result, replace with the result and also cache the result
                        youtubeCache.put(URL, result);
                        performLinkReplacement(theme, post, result, URL, toInvalidate);
                    }
                }, reader -> {
                    reader.beginObject(); // JSON start
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
                        ret = new Pair<>(title, getHourMinSecondString(reader.nextString()));
                        reader.endObject();
                    }
                    reader.endObject();
                    reader.endArray();
                    reader.endObject();
                    return ret;
                }, 2500));
            }
        }
        return calls;
    }

    private static String getHourMinSecondString(String ISO8601Duration) {
        Matcher m = iso8601Time.matcher(ISO8601Duration);
        if (m.matches()) {
            String hours = m.group(2);
            String minutes = m.group(4);
            String seconds = m.group(6);
            //pad seconds to 2 digits
            seconds = seconds != null ? (seconds.length() == 1 ? "0" + seconds : seconds) : "00";
            if (hours != null) {
                //we have hours, pad minutes to 2 digits
                minutes = minutes != null ? (minutes.length() == 1 ? "0" + minutes : minutes) : null;
                return hours + ":" + (minutes != null ? minutes : "00") + ":" + seconds;
            } else {
                //no hours, no need to pad anything else
                return (minutes != null ? minutes : "0") + ":" + seconds;
            }
        } else {
            //badly formatted time from youtube's API?
            return "?:??";
        }
    }

    private static void performLinkReplacement(
            Theme theme, Post post, @NonNull Pair<String, String> titleDurPair, String URL, TextView toSetAndInvalidate
    ) {
        synchronized (post.comment) {
            int startIndex = post.comment.toString().indexOf(URL);
            int endIndex = startIndex + URL.length();

            if (startIndex == -1) {
                return; // don't know where to do replacement
            }

            SpannableStringBuilder replacement = new SpannableStringBuilder(
                    "  " + titleDurPair.first + (titleDurPair.second != null ? " [" + titleDurPair.second + "]" : ""));

            //set the youtube icon span for the linkable
            ImageSpan ytIcon = new ImageSpan(getAppContext(), BitmapRepository.youtubeIcon);
            int height = Integer.parseInt(ChanSettings.fontSize.get());
            int width =
                    (int) (sp(height) / (BitmapRepository.youtubeIcon.getHeight() / (float) BitmapRepository.youtubeIcon
                            .getWidth()));
            ytIcon.getDrawable().setBounds(0, 0, width, sp(height));
            replacement.setSpan(
                    ytIcon,
                    0,
                    1,
                    ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY) | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            );

            //set the linkable to be the entire length, including the icon
            PostLinkable pl = new PostLinkable(theme, replacement, URL, PostLinkable.Type.LINK);
            replacement.setSpan(
                    pl,
                    0,
                    replacement.length(),
                    ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY) | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            );

            //replace the proper section of the comment with the link
            post.comment.replace(startIndex, endIndex, replacement);

            post.linkables.add(pl);

            if (toSetAndInvalidate != null) {
                toSetAndInvalidate.setText(post.comment);
                toSetAndInvalidate.postInvalidate();
            }
        }
    }
}

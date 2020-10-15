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
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.util.LruCache;
import android.widget.TextView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.utils.JavaUtils;
import com.github.adamantcheese.chan.utils.NetUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jetbrains.annotations.NotNull;
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
import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

@AnyThread
public class CommentParserHelper {
    private static final LinkExtractor LINK_EXTRACTOR =
            LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

    // a cache for titles and durations to prevent extra api calls if not necessary
    // maps a URL to a title and duration string; if durations are disabled, the second argument is an empty string
    public static LruCache<String, Pair<String, String>> videoTitleDurCache = new LruCache<>(500);
    // maps a math string to a rendered image URL
    public static LruCache<String, HttpUrl> mathCache = new LruCache<>(100);

    private static final String[] ignoreURLs = {"youtu.be", "youtube", "streamable", "vocaroo", "voca.ro", "clyp.it"};

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
            final String scheme = linkText.substring(0, linkText.indexOf(':'));
            if (!"http".equals(scheme) && !"https".equals(scheme)) continue; // only autolink URLs, not any random URI
            // if this URL is a video link and we're parsing those, skip it, it'll be taken care of later
            // cheap match instead of full matcher for speed
            if (ChanSettings.parseMediaTitles.get() && StringUtils.containsAny(linkText, ignoreURLs)) {
                post.needsExtraParse = true;
                continue;
            }
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

    //region Image Inlining
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*/(.+?)\\.(jpg|png|jpeg|gif|webm|mp4|pdf|bmp|webp|mp3|swf|m4a|ogg|flac)",
            Pattern.CASE_INSENSITIVE
    );
    private static final String[] noThumbLinkSuffixes = {"webm", "pdf", "mp4", "mp3", "swf", "m4a", "ogg", "flac"};

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
                        if (imageUrl == null
                                || ((String) linkable.value).contains("saucenao")) { // ignore saucenao links, not actual images
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
    //endregion

    private static final Pattern dubsPattern = Pattern.compile("(\\d)\\1$");
    private static final Pattern tripsPattern = Pattern.compile("(\\d)\\1{2}$");
    private static final Pattern quadsPattern = Pattern.compile("(\\d)\\1{3}$");
    private static final Pattern quintsPattern = Pattern.compile("(\\d)\\1{4}$");
    private static final Pattern hexesPattern = Pattern.compile("(\\d)\\1{5}$");
    private static final Pattern septsPattern = Pattern.compile("(\\d)\\1{6}$");
    private static final Pattern octsPattern = Pattern.compile("(\\d)\\1{7}$");
    private static final Pattern nonsPattern = Pattern.compile("(\\d)\\1{8}$");
    private static final Pattern decsPattern = Pattern.compile("(\\d)\\1{9}$");

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

    /**
     * To add a media link parser:<br>
     * 1) Add in your icon to BitmapRepository<br>
     * 2) Add it to the list in the iconMatchesAny call<br>
     * 3) Add a simple URL check to the ignoreURLs string array above detectLinks (so that autolinking isn't done up there, as it's taken care of down here instead)<br>
     * 4) Add a new method to process your site and add it to the calls.addAll list<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;
     * - The helper method addMediaCalls should be used for most stuff, it takes in all the appropriate stuff you'll need to implement functionality<br>
     * 5) Done! Everything else is taken care of for you.<br>
     * <br>
     *
     * @param theme              The theme to style the links with
     * @param post               The post where the links will be found and replaced
     * @param invalidateFunction The entire view to be refreshed after embedding
     */

    public static List<Call> replaceMediaLinks(
            Theme theme, @NonNull Post post, @NonNull InvalidateFunction invalidateFunction
    ) {
        // if we've already got an image span with a youtube/streamable link in it, this post has already been processed/is processing, ignore this
        ImageSpan[] imageSpans = post.comment.getSpans(0, post.comment.length() - 1, ImageSpan.class);
        for (ImageSpan image : imageSpans) {
            if (image.getDrawable() instanceof BitmapDrawable) {
                if (JavaUtils.objectMatchesAny(((BitmapDrawable) image.getDrawable()).getBitmap(),
                        BitmapRepository.youtubeIcon,
                        BitmapRepository.streamableIcon,
                        BitmapRepository.clypIcon
                )) {
                    return null;
                }
            }
        }

        List<Pair<Call, Callback>> toCall = new ArrayList<>();
        toCall.addAll(addYoutubeCalls(theme, post, invalidateFunction));
        toCall.addAll(addStreamableCalls(theme, post, invalidateFunction));
        toCall.addAll(addVocarooCalls(post, invalidateFunction));
        toCall.addAll(addClypCalls(theme, post, invalidateFunction));

        List<Call> calls = new ArrayList<>();
        for (Pair<Call, Callback> c : toCall) { // enqueue all at the same time
            if (c.first == null || c.second == null) throw new IllegalArgumentException("???");
            c.first.enqueue(c.second);
            calls.add(c.first);
        }
        return calls;
    }

    //region Youtube Parsing
    //for testing, using 4chanx's api key
    //normal https://www.googleapis.com/youtube/v3/videos?part=snippet&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE
    //duration https://www.googleapis.com/youtube/v3/videos?part=snippet%2CcontentDetails&id=dQw4w9WgXcQ&fields=items%28id%2Csnippet%28title%29%2CcontentDetails%28duration%29%29&key=AIzaSyB5_zaen_-46Uhz1xGR-lz1YoUMHqCD6CE

    /* SAMPLE JSON FOR YOUTUBE WITH DURATION (skip the contentDetails stuff for without)
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

    private static final Pattern YOUTUBE_LINK_PATTERN = Pattern.compile(
            "https?://(?:youtu\\.be/|[\\w.]*youtube[\\w.]*/.*?(?:v=|\\bembed/|\\bv/))([\\w\\-]{11})([^\\s]*)\\b");

    private static List<Pair<Call, Callback>> addYoutubeCalls(
            Theme theme, Post post, @NonNull InvalidateFunction invalidateFunction
    ) {
        return addMediaCalls(theme,
                post,
                invalidateFunction,
                false,
                YOUTUBE_LINK_PATTERN,
                matcher -> HttpUrl.get(
                        "https://www.googleapis.com/youtube/v3/videos?part=snippet" + (ChanSettings.parseYoutubeDuration
                                .get() ? "%2CcontentDetails" : "") + "&id=" + matcher.group(1)
                                + "&fields=items%28id%2Csnippet%28title%29" + (ChanSettings.parseYoutubeDuration.get()
                                ? "%2CcontentDetails%28duration%29"
                                : "") + "%29&key=" + ChanSettings.parseYoutubeAPIKey.get()),
                BitmapRepository.youtubeIcon,
                (reader) -> {
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
                    return new ParseReturnStruct(false, ret);
                }
        );
    }

    private static final Pattern iso8601Time = Pattern.compile("PT((\\d+)H)?((\\d+)M)?((\\d+)S)?");

    private static String getHourMinSecondString(String ISO8601Duration) {
        Matcher m = iso8601Time.matcher(ISO8601Duration);
        String ret;
        if (m.matches()) {
            String hours = m.group(2);
            String minutes = m.group(4);
            String seconds = m.group(6);
            //pad seconds to 2 digits
            seconds = seconds != null ? (seconds.length() == 1 ? "0" + seconds : seconds) : "00";
            if (hours != null) {
                //we have hours, pad minutes to 2 digits
                minutes = minutes != null ? (minutes.length() == 1 ? "0" + minutes : minutes) : null;
                ret = hours + ":" + (minutes != null ? minutes : "00") + ":" + seconds;
            } else {
                //no hours, no need to pad anything else
                ret = (minutes != null ? minutes : "00") + ":" + seconds;
            }
        } else if ("P0D".equals(ISO8601Duration)) {
            ret = "LIVE";
        } else {
            //badly formatted time from youtube's API?
            ret = "??:??";
        }

        return "[" + ret + "]";
    }
    //endregion

    //region Streamable Parsing
    /* SAMPLE JSON FOR STREAMABLE; note that this API is not well documented
    {
       "status": 2,
       "percent": 100,
       "url": "streamable.com/uhoe7l",
       "embed_code": "<div style=\"width: 100%; height: 0p...</div>",
       "message": null, PRETTY MUCH ALWAYS NULL
       "files": {
          "mp4": {
              "status": 2,
              "url": "https://cdn-cf-east.streamable.com...",
              "framerate": 30,
              "height": 720,
              "width": 1280,
              "bitrate": 2067499,
              "size": 150190909,
              "duration": 581.147233
          },
          "original": {
              "framerate": 29.97002997002997,
              "bitrate": 2063218,
              "size": 149879643,
              "duration": 581.147233,
              "height": 720,
              "width": 1280
          }
       },
       "thumbnail_url": "//cdn-cf-east.streamable.com/image/uhoe7l.jpg",
       "title": "",
       "source": "https://www.youtube.com/watch?v=Unnvj58sP3I" MAY BE NULL
    }
     */
    private static final Pattern STREAMABLE_LINK_PATTERN =
            Pattern.compile("https?://(?:www\\.)?streamable\\.com/(.{6})\\b");

    private static List<Pair<Call, Callback>> addStreamableCalls(
            Theme theme, Post post, @NonNull InvalidateFunction toInvalidate
    ) {
        return addMediaCalls(theme,
                post,
                toInvalidate,
                ChanSettings.parsePostImageLinks.get(),
                STREAMABLE_LINK_PATTERN,
                matcher -> HttpUrl.get("https://api.streamable.com/videos/" + matcher.group(1)),
                BitmapRepository.streamableIcon,
                (reader) -> {
                    String serverFilename = "";
                    HttpUrl mp4Url = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");
                    HttpUrl thumbnailUrl = null;
                    long size = -1L;

                    String title = "titleMissing" + Math.random();
                    double duration = Double.NaN;

                    reader.beginObject(); // JSON start
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        switch (name) {
                            case "url":
                                serverFilename = reader.nextString();
                                serverFilename = serverFilename.substring(serverFilename.indexOf('/') + 1);
                                break;
                            case "files":
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    String format = reader.nextName();
                                    if ("mp4".equals(format)) {
                                        reader.beginObject();
                                        while (reader.hasNext()) {
                                            String innerName = reader.nextName();
                                            switch (innerName) {
                                                case "duration":
                                                    duration = reader.nextDouble();
                                                    break;
                                                case "url":
                                                    mp4Url = HttpUrl.get(reader.nextString());
                                                    break;
                                                case "size":
                                                    size = reader.nextLong();
                                                    break;
                                                default:
                                                    reader.skipValue();
                                                    break;
                                            }
                                        }
                                        reader.endObject();
                                    } else {
                                        reader.skipValue();
                                    }
                                }
                                reader.endObject();
                                break;
                            case "title":
                                title = reader.nextString();
                                break;
                            case "thumbnail_url":
                                thumbnailUrl = HttpUrl.get("https:" + reader.nextString());
                                break;
                            default:
                                reader.skipValue();
                                break;
                        }
                    }
                    reader.endObject();

                    boolean needsRefresh = false;
                    if (ChanSettings.parsePostImageLinks.get()) {
                        PostImage inlinedImage = new PostImage.Builder().serverFilename(serverFilename)
                                .thumbnailUrl(thumbnailUrl)
                                .imageUrl(mp4Url)
                                .filename(title)
                                .extension("mp4")
                                .isInlined(true)
                                .size(size)
                                .build();

                        post.addImage(inlinedImage);
                        needsRefresh = true;
                    }

                    return new ParseReturnStruct(needsRefresh,
                            new Pair<>(title, "[" + DateUtils.formatElapsedTime(Math.round(duration)) + "]")
                    );
                }
        );
    }
    //endregion

    //region Vocaroo Parsing
    private static final Pattern VOCAROO_LINK_PATTERN =
            Pattern.compile("https?://(?:vocaroo\\.com|voca\\.ro)/(\\w{12})\\b");

    private static List<Pair<Call, Callback>> addVocarooCalls(
            Post post, @NonNull InvalidateFunction invalidateFunction
    ) {
        if (ChanSettings.parsePostImageLinks.get()) {
            boolean added = false;
            Matcher linkMatcher = VOCAROO_LINK_PATTERN.matcher(post.comment);
            while (linkMatcher.find()) {
                String URL = linkMatcher.group(0);
                if (URL == null) continue;

                PostImage inlinedImage = new PostImage.Builder().serverFilename(linkMatcher.group(1))
                        .thumbnailUrl(HttpUrl.parse(
                                "https://vocarooblog.files.wordpress.com/2020/04/robotchibi-cropped-1.png"))
                        .imageUrl(HttpUrl.get("https://media1.vocaroo.com/mp3/" + linkMatcher.group(1)))
                        .filename("Vocaroo " + linkMatcher.group(1))
                        .extension("mp3")
                        .isInlined(true)
                        .build();

                post.addImage(inlinedImage);

                synchronized (post.comment) {
                    int startIndex = post.comment.toString().indexOf(URL);
                    int endIndex = startIndex + URL.length();
                    SpannableStringBuilder builder = new SpannableStringBuilder();
                    builder.append("♫ Vocaroo attached! ♫");
                    builder.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), 0, builder.length(), 0);
                    post.comment.replace(startIndex, endIndex, builder);
                }

                added = true;
            }

            if (added) {
                invalidateFunction.invalidate(true);
            }
        }

        return Collections.emptyList();
    }
    //endregion

    //region Clyp.it Parsing
    /* EXAMPLE JSON
    {
      "Status": "DownloadDisabled",
      "CommentsEnabled": true,
      "Category": "None",
      "AudioFileId": "j42441xr",
      "Title": "ob6 + piano + bigsky",
      "Description": "first encounter with the big sky",
      "Duration": 67.709,
      "Url": "https://clyp.it/j42441xr",
      "Mp3Url": "https://audio.clyp.it/j42441xr.mp3?Exp...",
      "SecureMp3Url": "https://audio.clyp.it/j42441xr.mp3?Exp...",
      "OggUrl": "https://audio.clyp.it/j42441xr.ogg?Exp...",
      "SecureOggUrl": "https://audio.clyp.it/j42441xr.ogg?Exp...",
      "DateCreated": "2020-09-20T05:16:29.473Z"
    }
     */
    private static final Pattern CLYP_LINK_PATTERN = Pattern.compile("https?://clyp.it/(\\w{8})");

    private static List<Pair<Call, Callback>> addClypCalls(
            Theme theme, Post post, @NonNull InvalidateFunction invalidateFunction
    ) {
        return addMediaCalls(theme,
                post,
                invalidateFunction,
                ChanSettings.parsePostImageLinks.get(),
                CLYP_LINK_PATTERN,
                (matcher -> HttpUrl.get("https://api.clyp.it/" + matcher.group(1))),
                BitmapRepository.clypIcon,
                (reader -> {
                    String title = "titleMissing" + Math.random();
                    double duration = Double.NaN;

                    HttpUrl mp3Url = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");
                    String fileId = "";

                    reader.beginObject();
                    while (reader.hasNext()) {
                        String name = reader.nextName();
                        switch (name) {
                            case "Title":
                                title = reader.nextString();
                                break;
                            case "Duration":
                                duration = reader.nextDouble();
                                break;
                            case "AudioFileId":
                                fileId = reader.nextString();
                                break;
                            case "Mp3Url":
                                mp3Url = HttpUrl.get(reader.nextString());
                                break;
                            default:
                                reader.skipValue();
                                break;
                        }
                    }
                    reader.endObject();

                    boolean needsRefresh = false;
                    if (ChanSettings.parsePostImageLinks.get()) {
                        PostImage inlinedImage = new PostImage.Builder().serverFilename(fileId)
                                .thumbnailUrl(HttpUrl.get(
                                        "https://static.clyp.it/site/images/favicons/apple-touch-icon-precomposed.png"))
                                .imageUrl(mp3Url)
                                .filename(title)
                                .extension("mp3")
                                .isInlined(true)
                                .build();

                        post.addImage(inlinedImage);
                        needsRefresh = true;
                    }

                    return new ParseReturnStruct(needsRefresh,
                            new Pair<>(title, "[" + DateUtils.formatElapsedTime(Math.round(duration)) + "]")
                    );
                })
        );
    }
    //endregion

    //region Helper and Internal Functions
    private static List<Pair<Call, Callback>> addMediaCalls(
            Theme theme,
            Post post,
            @NonNull InvalidateFunction invalidateFunction,
            boolean alwaysRequest,
            Pattern pattern,
            RequestURLGenerator generator,
            Bitmap icon,
            NetUtils.JsonParser<ParseReturnStruct> apiParser
    ) {
        List<Pair<Call, Callback>> calls = new ArrayList<>();
        //find and replace all video URLs with their titles, but keep track in the map above for spans later
        Matcher linkMatcher = pattern.matcher(post.comment);
        while (linkMatcher.find()) {
            String URL = linkMatcher.group(0);
            if (URL == null) continue;
            HttpUrl requestUrl = generator.generateUrl(linkMatcher);

            boolean needsRequest = alwaysRequest;
            Pair<String, String> result = videoTitleDurCache.get(URL);
            if (result != null) {
                if (result.second == null) {
                    // remove the entry; it needs additional info now
                    videoTitleDurCache.remove(URL);
                    needsRequest = true;
                }
            } else {
                needsRequest = true;
            }

            if (!needsRequest) {
                // we've previously cached this title/duration and we don't need additional information
                performVideoLinkReplacement(theme,
                        post,
                        new ParseReturnStruct(false, result),
                        URL,
                        invalidateFunction,
                        icon
                );
            } else {
                // we haven't cached this media title/duration, or we need additional information
                calls.add(NetUtils.makeJsonCall(requestUrl, new NetUtils.JsonResult<ParseReturnStruct>() {
                    @Override
                    public void onJsonFailure(Exception e) {
                        if (!"Canceled".equals(e.getMessage())) {
                            //failed to get, replace with just the URL and append the icon
                            performVideoLinkReplacement(theme,
                                    post,
                                    new ParseReturnStruct(false, new Pair<>(URL, null)),
                                    URL,
                                    invalidateFunction,
                                    icon
                            );
                        }
                    }

                    @Override
                    public void onJsonSuccess(ParseReturnStruct result) {
                        //got a result, replace with the result and also cache the result
                        videoTitleDurCache.put(URL, result.titleDurPair);
                        performVideoLinkReplacement(theme, post, result, URL, invalidateFunction, icon);
                    }
                }, apiParser, 2500));
            }
        }
        return calls;
    }

    private interface RequestURLGenerator {
        HttpUrl generateUrl(Matcher matcher);
    }

    private static class ParseReturnStruct {
        boolean fullRefresh;
        // if the second item in this pair is null, another request will be sent
        // keep it as the empty string generally if there will never be a duration
        Pair<String, String> titleDurPair;

        public ParseReturnStruct(boolean fullRefresh, Pair<String, String> titleDurPair) {
            this.fullRefresh = fullRefresh;
            this.titleDurPair = titleDurPair;
        }
    }

    private static void performVideoLinkReplacement(
            Theme theme,
            Post post,
            @NonNull ParseReturnStruct parseResult,
            @NonNull String URL,
            @NonNull InvalidateFunction invalidateFunction,
            Bitmap icon
    ) {
        synchronized (post.comment) {
            int startIndex = post.comment.toString().indexOf(URL);
            int endIndex = startIndex + URL.length();

            if (startIndex == -1) {
                return; // don't know where to do replacement
            }

            SpannableStringBuilder replacement = new SpannableStringBuilder(
                    "  " + parseResult.titleDurPair.first + (!TextUtils.isEmpty(parseResult.titleDurPair.second) ? " "
                            + parseResult.titleDurPair.second : ""));

            //set the icon span for the linkable
            ImageSpan siteIcon = new ImageSpan(getAppContext(), icon);
            int height = sp(ChanSettings.fontSize.get());
            int width = (int) (height / (icon.getHeight() / (float) icon.getWidth()));
            siteIcon.getDrawable().setBounds(0, 0, width, height);
            replacement.setSpan(siteIcon,
                    0,
                    1,
                    ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY) | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            );

            //set the linkable to be the entire length, including the icon
            PostLinkable pl = new PostLinkable(theme, replacement, URL, PostLinkable.Type.LINK);
            replacement.setSpan(pl,
                    0,
                    replacement.length(),
                    ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY) | Spanned.SPAN_INCLUSIVE_EXCLUSIVE
            );

            //replace the proper section of the comment with the link
            post.comment.replace(startIndex, endIndex, replacement);

            post.linkables.add(pl);

            invalidateFunction.invalidate(parseResult.fullRefresh);
        }
    }

    /**
     * The invalidate callback for video call replacements; if fullInvalidate is set, the callback should invalidate the
     * entire view rather than just updating the textview for the post comment that was modified (ie calling setText and invalidate)
     */
    public interface InvalidateFunction {
        void invalidate(boolean fullInvalidate);
    }
    //endregion

    //region Math/Eqn Parsing
    private static final Pattern MATH_EQN_PATTERN = Pattern.compile("\\[(?:math|eqn)].*?\\[/(?:math|eqn)]");
    private static final Pattern QUICK_LATEX_RESPONSE =
            Pattern.compile(".*?\r\n(\\S+)\\s.*?\\s\\d+\\s\\d+(?:\r\n([\\s\\S]+))?");

    public static void addMathSpans(Post post, @Nullable TextView toInvalidate) {
        Matcher linkMatcher = MATH_EQN_PATTERN.matcher(post.comment);
        while (linkMatcher.find()) {
            final String math = linkMatcher.group(0);
            if (math == null) continue;

            String rawMath = math.replace("[math]", "$")
                    .replace("[eqn]", "$$")
                    .replace("[/math]", "$")
                    .replace("[/eqn]", "$$")
                    .replace("%", "%25")
                    .replace("&", "%26");
            HttpUrl imageUrl = mathCache.get(math);
            if (imageUrl != null) {
                // have a previous image URL
                performMathSpanAdditions(post, imageUrl, math, toInvalidate);
            } else {
                // need to request an image URL
                Call call = instance(NetModule.OkHttpClientWithUtils.class).newCall(setupMathImageUrlRequest(rawMath));
                call.enqueue(new NetUtils.IgnoreFailureCallback() {
                    @Override
                    public void onResponse(@NotNull Call call, @NotNull Response response) {
                        if (!response.isSuccessful()) {
                            response.close();
                            return;
                        }

                        try (ResponseBody body = response.body()) {
                            //noinspection ConstantConditions
                            String responseString = body.string();
                            Matcher matcher = QUICK_LATEX_RESPONSE.matcher(responseString);
                            if (matcher.matches()) {
                                //noinspection ConstantConditions
                                HttpUrl url = HttpUrl.get(matcher.group(1));
                                String err = matcher.group(2);
                                if (err == null) {
                                    mathCache.put(math, url);
                                    performMathSpanAdditions(post, url, math, toInvalidate);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        }
    }

    private static Request setupMathImageUrlRequest(String formula) {
        Request.Builder requestBuilder = new Request.Builder();

        //noinspection StringBufferReplaceableByString
        StringBuilder postBody = new StringBuilder();
        postBody.append("formula=")
                .append(formula)
                .append("&fsize=")
                .append((int) (sp(ChanSettings.fontSize.get()) * 1.2))
                .append("px")
                .append("&fcolor=")
                .append(String.format("%06X",
                        (0xFFFFFF & getAttrColor(ThemeHelper.getTheme().resValue, android.R.attr.textColor))
                ))
                .append("&mode=0")
                .append("&out=1")
                .append("&preamble=")
                .append("\\usepackage{amsmath}\r\n\\usepackage{amsfonts}\r\n\\usepackage{amssymb}")
                .append("&rnd=")
                .append(Math.random() * 100)
                .append("&remhost=quicklatex.com");

        RequestBody body = RequestBody.create(postBody.toString(), null);

        requestBuilder.url("https://www.quicklatex.com/latex3.f");
        requestBuilder.post(body);

        return requestBuilder.build();
    }

    private static void performMathSpanAdditions(
            Post post, @NonNull final HttpUrl imageUrl, final String rawMath, @Nullable TextView toInvalidate
    ) {
        NetUtils.makeBitmapRequest(imageUrl, new NetUtils.BitmapResult() {
            @Override
            public void onBitmapFailure(Bitmap errormap, Exception e) {}

            @Override
            public void onBitmapSuccess(@NonNull Bitmap bitmap, boolean fromCache) {
                synchronized (post.comment) {
                    for (int i = 0; i < post.comment.length(); ) {
                        int startIndex = post.comment.toString().indexOf(rawMath, i);
                        int endIndex = startIndex + rawMath.length();

                        i = endIndex + 1;

                        if (startIndex == -1) {
                            return; // don't know where to do replacement or finished all replacements (in the case of multiple of the same latex)
                        }

                        if (post.comment.getSpans(startIndex, endIndex, ImageSpan.class).length > 0) {
                            continue; // we've already got an image span attached here
                        }

                        ImageSpan mathImage = new ImageSpan(getAppContext(), bitmap);
                        post.comment.setSpan(mathImage,
                                startIndex,
                                endIndex,
                                ((500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY)
                                        | Spanned.SPAN_INCLUSIVE_INCLUSIVE
                        );

                        if (toInvalidate != null) {
                            toInvalidate.setText(post.comment);
                            toInvalidate.postInvalidate();
                        }
                    }
                }
            }
        });
    }
    //endregion
}

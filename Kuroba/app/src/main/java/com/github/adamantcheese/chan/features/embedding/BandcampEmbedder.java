package com.github.adamantcheese.chan.features.embedding;

import android.graphics.Bitmap;

import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.EmbedResult;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import org.jsoup.nodes.Document;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.features.embedding.EmbeddingEngine.addHTMLEmbedCalls;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.StringUtils.getRGBColorIntString;

public class BandcampEmbedder
        implements Embedder<Document> {
    // note that for this pattern, we allow for non-album/track links, but in the case of a bandcamp page that links directly
    // to the music overview, the parse will fail, so don't add in a hotlink
    private static final Pattern BANDCAMP_PATTERN =
            Pattern.compile("https?://(?:\\w+\\.)?bandcamp\\.com(?:/.*)?(?:/|\\b)");
    private static final Pattern ALBUMTRACKID_PATTERN = Pattern.compile("(?:album|track)=(\\d+)");

    @Override
    public List<String> getShortRepresentations() {
        return Collections.singletonList("bandcamp");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.bandcampIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return BANDCAMP_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get(matcher.group(0));
    }

    @Override
    public List<Pair<Call, Callback>> generateCallPairs(Theme theme, Post post) {
        return addHTMLEmbedCalls(this, theme, post);
    }

    @Override
    public EmbedResult process(Document response)
            throws Exception {
        PostImage extra = null;
        try {
            String extractedPlayer = response.select("meta[property=twitter:player]").get(0).attr("content");
            Matcher idMatcher = ALBUMTRACKID_PATTERN.matcher(extractedPlayer);
            HttpUrl embeddedPlayer = HttpUrl.get(response.location());
            if (idMatcher.find()) {
                //noinspection ConstantConditions
                long id = Long.parseLong(idMatcher.group(1));
                boolean isAlbum = extractedPlayer.contains("album");
                String constructedUrl = "https://bandcamp.com/EmbeddedPlayer/" + (isAlbum ? "album=" : "track=") + id
                        + "/size=large/bgcol=" + getRGBColorIntString(getAttrColor(ThemeHelper.getTheme().resValue,
                        R.attr.backcolor
                )) + "/linkcol=" + getRGBColorIntString(getAttrColor(ThemeHelper.getTheme().resValue,
                        android.R.attr.textColorPrimary
                )) + "/minimal=true/transparent=true/";

                embeddedPlayer = HttpUrl.get(constructedUrl);
            }
            extra = new PostImage.Builder().serverFilename(response.title())
                    .thumbnailUrl(HttpUrl.get(response.select("#tralbumArt > .popupImage").get(0).attr("href")))
                    .imageUrl(embeddedPlayer)
                    .filename(response.title())
                    .extension("iframe")
                    .isInlined(true)
                    .build();
        } catch (Exception ignored) {
            // no player on this page
        }

        return new EmbedResult(response.title(), "", extra);
    }
}

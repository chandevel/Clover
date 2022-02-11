package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class DlsiteEmbedder
        extends JsonEmbedder {

    private final Pattern DLSITE_PATTERN =
            Pattern.compile("(?:http|www|dlsite)[^>\\s]+[rR][jJ](\\d{6})(?:\\.html)?");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "dlsite");
    }

    @Override
    public Bitmap getIconBitmap() {
        return BitmapRepository.dlsiteIcon;
    }

    @Override
    public Pattern getEmbedReplacePattern() {
        return DLSITE_PATTERN;
    }

    @Override
    public HttpUrl generateRequestURL(Matcher matcher) {
        return HttpUrl.get("https://www.dlsite.com/maniax/product/info/ajax?product_id=RJ" + matcher.group(1));
    }

    /* There's a ton of info returned in the API, only what matters is listed
       Note that images are stored in buckets indicated by the first RJ code in the URL
       It's the first 3 digits of the actual RJ code + 1 (basically rounded up to the next 1000)

    {
        RJ****** {
           "down_url": https://www.dlsite.com/maniax/download/=/product_id/RJ******.html,
           "title_name": this actually seems to be the series name,
           "work_name": the name of the work,
           "work_image": //img.dlsite.jp/modpub/images2/work/doujin/RJ***000/RJ******_img_main.jpg,
        }
    }
    */

    @Override
    public NetUtilsClasses.Converter<EmbedResult, JsonReader> getInternalConverter() {
        return input -> {
            String title = "dlsiteImage";
            String duration = "";
            String serverFilename = "";
            String imageFilename = "";
            HttpUrl sourceUrl = HttpUrl.get(BuildConfig.RESOURCES_ENDPOINT + "internal_spoiler.png");

            input.beginObject();
            while (input.hasNext()) {
                serverFilename = input.nextName();
                input.beginObject();
                while(input.hasNext()) {
                    String name = input.nextName();
                    switch (name) {
                        case "work_name":
                            title = input.nextString();
                            break;
                        case "work_image":
                            String thumbLocation = input.nextString();
                            sourceUrl = HttpUrl.get("https:" + thumbLocation);
                            imageFilename = thumbLocation.substring(thumbLocation.lastIndexOf("/") + 1, thumbLocation.lastIndexOf("/") + 18);
                            break;
                        default:
                            input.skipValue();
                            break;
                    }
                }
                input.endObject();
            }
            input.endObject();
            return new EmbedResult(
                    title,
                    duration,
                    new PostImage.Builder().serverFilename(serverFilename)
                            .thumbnailUrl(sourceUrl)
                            .imageUrl(sourceUrl)
                            .filename(imageFilename)
                            .extension("jpg")
                            .isInlined()
                            .build()
            );
        };
    }
}

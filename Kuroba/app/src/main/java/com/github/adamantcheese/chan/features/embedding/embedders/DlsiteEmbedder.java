package com.github.adamantcheese.chan.features.embedding.embedders;

import android.graphics.Bitmap;
import android.util.JsonReader;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.features.embedding.EmbedResult;
import com.github.adamantcheese.chan.utils.StringUtils;
import com.google.common.io.Files;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

public class DlsiteEmbedder
        extends JsonEmbedder {

    // This pattern matches either links in the form of https://dlsite.com/.../rj******, or rjcodes in the form of rj****** where * is a digit
    private final Pattern DLSITE_PATTERN =
            Pattern.compile("https?://(?:www\\.)?dlsite\\.com.+?[rR][jJ](\\d{6})(?:\\.html)?|(?<!/)[rR][jJ](\\d{6})(?![^ .,?!:;)\\]'\"\\-=+_/\\n])");

    @Override
    public boolean shouldEmbed(CharSequence comment) {
        return StringUtils.containsAny(comment, "dlsite", "RJ", "rj", "Rj", "rJ");
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
        // Since the match can either be a link or an rjcode, need to find the correct group
        // Group 1 indicates a https://dlsite.com/.../rj****** match, group 2 indicates an rjcode match
        // Neither matching shouldn't ever happen but just in case, it defaults to 000000 which does nothing
        String rjcode = "000000";
        if (matcher.group(1) == null) rjcode = matcher.group(2);
        else if (matcher.group(2) == null) rjcode = matcher.group(1);
        return HttpUrl.get("https://www.dlsite.com/maniax/product/info/ajax?product_id=RJ" + rjcode);
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
            String fileExtension = "jpg";
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
                            fileExtension = Files.getFileExtension(thumbLocation);
                            sourceUrl = HttpUrl.get("https:" + thumbLocation);
                            imageFilename = thumbLocation.substring(thumbLocation.lastIndexOf("/") + 1, thumbLocation.lastIndexOf('.'));
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
                            .extension(fileExtension)
                            .isInlined()
                            .build()
            );
        };
    }
}

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
package com.github.adamantcheese.chan.core.net;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.JsonReader;
import android.util.MalformedJsonException;

import androidx.core.text.HtmlCompat;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses.JSONProcessor;
import com.github.adamantcheese.chan.core.net.UpdateApiParser.UpdateApiResponse;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.BuildConfig.DEV_BUILD;
import static com.github.adamantcheese.chan.BuildConfig.DEV_GITHUB_ENDPOINT;
import static com.github.adamantcheese.chan.BuildConfig.GITHUB_ENDPOINT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;

public class UpdateApiParser
        extends JSONProcessor<UpdateApiResponse> {
    private final String versionPatternString = "v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})";
    private final Pattern RELEASE_PATTERN = Pattern.compile(versionPatternString);
    private final Pattern DEV_PATTERN = Pattern.compile(versionPatternString + "-(.*)");

    @Override
    public UpdateApiResponse process(JsonReader reader)
            throws Exception {
        return DEV_BUILD ? parseDev(reader) : parseRelease(reader);
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public UpdateApiResponse parseRelease(JsonReader reader)
            throws IOException {
        UpdateApiResponse response = new UpdateApiResponse();
        //default body
        response.body = HtmlCompat.fromHtml(
                "Changelog:\r\nSee the release on Github for details!\r\n"
                        + " Your Android API is too low to properly render the changelog from the site.",
                HtmlCompat.FROM_HTML_MODE_LEGACY
        );

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tag_name":
                    response.versionCodeString = reader.nextString();
                    Matcher versionMatcher = RELEASE_PATTERN.matcher(response.versionCodeString);
                    if (versionMatcher.matches()) {
                        try {
                            //@formatter:off
                            response.versionCode =
                                    10000 * Integer.parseInt(versionMatcher.group(1)) +
                                    100   * Integer.parseInt(versionMatcher.group(2)) +
                                    1     * Integer.parseInt(versionMatcher.group(3));
                            response.apkURL =
                                    HttpUrl.get(GITHUB_ENDPOINT + "/releases/download/" +
                                    response.versionCodeString + "/" + getApplicationLabel() + ".apk");
                            //@formatter:on
                            break;
                        } catch (Exception e) {
                            throw new IOException("Error processing tag_name; apkUrl likely failed to be valid", e);
                        }
                    } else {
                        throw new MalformedJsonException("Error processing tag_name; version code string is not valid.");
                    }
                case "name":
                    response.updateTitle = reader.nextString();
                    break;
                case "body":
                    Node updateLog = Parser.builder().build().parse(reader.nextString());
                    response.body = HtmlCompat.fromHtml(
                            "Changelog:\r\n" + HtmlRenderer.builder().build().render(updateLog),
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                    );
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        return response;
    }

    @SuppressWarnings("PointlessArithmeticExpression")
    public UpdateApiResponse parseDev(JsonReader reader)
            throws IOException {
        UpdateApiResponse response = new UpdateApiResponse();
        response.body = SpannableStringBuilder.valueOf("New dev build; see commits!");

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tag_name":
                    response.versionCodeString = reader.nextString();
                    Matcher versionMatcher = DEV_PATTERN.matcher(response.versionCodeString);
                    if (versionMatcher.matches()) {
                        try {
                            //@formatter:off
                            response.versionCode =
                                    10000 * Integer.parseInt(versionMatcher.group(1)) +
                                    100   * Integer.parseInt(versionMatcher.group(2)) +
                                    1     * Integer.parseInt(versionMatcher.group(3));
                            response.apkURL =
                                    HttpUrl.get(DEV_GITHUB_ENDPOINT + "/releases/download/" +
                                            response.versionCodeString + "/" + getApplicationLabel() + "-" + versionMatcher.group(4) + ".apk");
                            //@formatter:on
                            break;
                        } catch (Exception e) {
                            throw new IOException("Error processing tag_name; apkUrl likely failed to be valid", e);
                        }
                    } else {
                        throw new MalformedJsonException("Error processing tag_name; version code string is not valid.");
                    }
                case "body":
                    response.commitHash = reader.nextString().trim();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        return response;
    }

    public static class UpdateApiResponse {
        public String commitHash = "";
        public int versionCode = 0;
        public String versionCodeString;
        public String updateTitle = "";
        public HttpUrl apkURL;
        public Spanned body;
    }
}

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

import android.os.Build;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.JsonReader;
import android.util.MalformedJsonException;

import com.github.adamantcheese.chan.core.net.UpdateApiParser.UpdateApiResponse;
import com.github.adamantcheese.chan.utils.NetUtils.JsonParser;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.HttpUrl;

import static android.text.Html.FROM_HTML_MODE_LEGACY;
import static com.github.adamantcheese.chan.BuildConfig.DEV_API_ENDPOINT;
import static com.github.adamantcheese.chan.BuildConfig.DEV_BUILD;
import static com.github.adamantcheese.chan.BuildConfig.GITHUB_ENDPOINT;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getApplicationLabel;

public class UpdateApiParser
        implements JsonParser<UpdateApiResponse> {

    @Override
    public UpdateApiResponse parse(JsonReader reader)
            throws Exception {
        if (DEV_BUILD) {
            return parseDev(reader);
        } else {
            return parseRelease(reader);
        }
    }

    @SuppressWarnings({"ConstantConditions", "PointlessArithmeticExpression"})
    public UpdateApiResponse parseRelease(JsonReader reader)
            throws IOException {
        UpdateApiResponse response = new UpdateApiResponse();
        //default body
        response.body = Html.fromHtml("Changelog:\r\nSee the release on Github for details!\r\n"
                + " Your Android API is too low to properly render the changelog from the site.");

        reader.beginObject();
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "tag_name":
                    response.versionCodeString = reader.nextString();
                    Pattern versionPattern = Pattern.compile("v(\\d+?)\\.(\\d{1,2})\\.(\\d{1,2})");
                    Matcher versionMatcher = versionPattern.matcher(response.versionCodeString);
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Node updateLog = Parser.builder().build().parse(reader.nextString());
                        response.body = Html.fromHtml(
                                "Changelog:\r\n" + HtmlRenderer.builder().build().render(updateLog),
                                FROM_HTML_MODE_LEGACY
                        );
                    }
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();
        return response;
    }

    public UpdateApiResponse parseDev(JsonReader reader)
            throws IOException {
        reader.beginObject();
        reader.nextName(); // apk_version
        int versionCode = reader.nextInt();
        reader.nextName(); // commit hash
        String commitHash = reader.nextString();
        reader.endObject();

        Matcher versionCodeMatcher = Pattern.compile("(\\d+)(\\d{2})(\\d{2})").matcher(String.valueOf(versionCode));
        if (versionCodeMatcher.matches()) {
            UpdateApiResponse response = new UpdateApiResponse();
            response.commitHash = commitHash;
            response.versionCode = versionCode;
            //@formatter:off
            //noinspection ConstantConditions
            response.versionCodeString =
                    "v" + Integer.valueOf(versionCodeMatcher.group(1))
                        + "." + Integer.valueOf(versionCodeMatcher.group(2))
                        + "." + Integer.valueOf(versionCodeMatcher.group(3))
                        + "-" + commitHash.substring(0, 7);
            //@formatter:on
            response.apkURL = HttpUrl.parse(DEV_API_ENDPOINT + "/apk/" + versionCode + "_" + commitHash + ".apk");
            response.body = SpannableStringBuilder.valueOf("New dev build; see commits!");
            return response;
        }
        return null;
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

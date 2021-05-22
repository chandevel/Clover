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
package com.github.adamantcheese.chan.core.site.common.vichan;

import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

/**
 * Vichan applies garbage looking fields to the post form, to combat bots.
 * Load up the normal html, parse the form, and get these fields for our post.
 */
public class VichanAntispam {
    private final HttpUrl url;

    private final List<String> fieldsToIgnore = new ArrayList<>();

    public VichanAntispam(HttpUrl url) {
        this.url = url;
    }

    public void addDefaultIgnoreFields() {
        Collections.addAll(
                fieldsToIgnore,
                "board",
                "thread",
                "name",
                "email",
                "subject",
                "body",
                "password",
                "file",
                "spoiler",
                "json_response",
                "file_url1",
                "file_url2",
                "file_url3"
        );
    }

    public void get(NetUtilsClasses.ResponseResult<Map<String, String>> callback) {
        NetUtils.makeCall(
                NetUtils.applicationClient,
                url,
                new NetUtilsClasses.ChainConverter<>((NetUtilsClasses.Converter<Map<String, String>, Document>) document -> {
                    Map<String, String> res = new HashMap<>();
                    Elements form = document.body().getElementsByTag("form");
                    for (Element element : form) {
                        if (element.attr("name").equals("post")) {
                            // Add all <input> and <textarea> elements.
                            Elements inputs = element.getElementsByTag("input");
                            inputs.addAll(element.getElementsByTag("textarea"));

                            for (Element input : inputs) {
                                String name = input.attr("name");
                                String value = input.val();

                                if (!fieldsToIgnore.contains(name)) {
                                    res.put(name, value);
                                }
                            }

                            break;
                        }
                    }
                    return res;
                }).chain(NetUtilsClasses.HTML_CONVERTER),
                callback,
                null,
                NetUtilsClasses.NO_CACHE,
                0,
                true
        );
    }
}

/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.net;


import android.util.JsonReader;

import com.android.volley.Response;

import org.floens.chan.BuildConfig;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.HttpUrl;

public class UpdateApiRequest extends JsonReaderRequest<UpdateApiRequest.UpdateApiResponse> {
    public static final String TYPE_UPDATE = "update";

    private static final int API_VERSION = 1;

    private String forFlavor;

    public UpdateApiRequest(Response.Listener<UpdateApiResponse> listener,
                            Response.ErrorListener errorListener) {
        super(BuildConfig.UPDATE_API_ENDPOINT, listener, errorListener);
        forFlavor = BuildConfig.FLAVOR;
    }

    @Override
    public UpdateApiResponse readJson(JsonReader reader) throws Exception {
        reader.beginObject();

        UpdateApiResponse response = new UpdateApiResponse();

        int apiVersion;
        out:
        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "api_version":
                    apiVersion = reader.nextInt();

                    if (apiVersion > API_VERSION) {
                        response.newerApiVersion = true;

                        while (reader.hasNext()) reader.skipValue();

                        break out;
                    }

                    break;
                case "messages":
                    reader.beginArray();
                    while (reader.hasNext()) {
                        response.messages.add(readMessage(reader));
                    }
                    reader.endArray();
                    break;
                case "check_interval":
                    response.checkIntervalMs = reader.nextLong();
                    break;
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        return response;
    }

    private UpdateApiMessage readMessage(JsonReader reader) throws IOException {
        reader.beginObject();

        UpdateApiMessage message = new UpdateApiMessage();

        while (reader.hasNext()) {
            switch (reader.nextName()) {
                case "type":
                    message.type = reader.nextString();
                    break;
                case "code":
                    message.code = reader.nextInt();
                    break;
                case "hash":
                    message.hash = reader.nextString();
                    break;
                case "date":
                    DateFormat format = new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    try {
                        message.date = format.parse(reader.nextString());
                    } catch (ParseException ignore) {
                    }
                    break;
                case "message_html":
                    message.messageHtml = reader.nextString();
                    break;
                case "apk":
                    reader.beginObject();
                    while (reader.hasNext()) {
                        if (reader.nextName().equals(forFlavor)) {
                            reader.beginObject();
                            while (reader.hasNext()) {
                                switch (reader.nextName()) {
                                    case "url":
                                        message.apkUrl = HttpUrl.parse(reader.nextString());
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
                default:
                    reader.skipValue();
                    break;
            }
        }

        reader.endObject();

        return message;
    }

    public static class UpdateApiResponse {
        public boolean newerApiVersion;
        public List<UpdateApiMessage> messages = new ArrayList<>();
        public long checkIntervalMs;
    }

    public static class UpdateApiMessage {
        public String type;
        public int code;
        public String hash;
        public Date date;
        public String messageHtml;
        public HttpUrl apkUrl;
    }
}

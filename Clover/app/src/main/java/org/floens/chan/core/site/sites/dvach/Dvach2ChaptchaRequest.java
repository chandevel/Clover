package org.floens.chan.core.site.sites.dvach;

import android.util.JsonReader;

import com.android.volley.Response;

import org.floens.chan.core.net.JsonReaderRequest;

public class Dvach2ChaptchaRequest extends JsonReaderRequest<String> {

    public Dvach2ChaptchaRequest(Response.Listener<String> listener, Response.ErrorListener errorListener) {
        super("https://2ch.hk/api/captcha/2chaptcha/id", listener, errorListener);
    }

    @Override
    public String readJson(JsonReader reader) throws Exception {
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.equals("id")) {
                return reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return "";
    }
}

package org.floens.chan.core.http;

import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.floens.chan.chan.ChanUrls;

import java.io.IOException;
import java.net.HttpCookie;
import java.util.List;

public class PassHttpCall extends HttpCall {
    public boolean success;
    public String message;
    public String passId;

    private String token;
    private String pin;

    public PassHttpCall(String token, String pin) {
        this.token = token;
        this.pin = pin;
    }

    @Override
    public void setup(Request.Builder requestBuilder) {
        FormEncodingBuilder formBuilder = new FormEncodingBuilder();

        formBuilder.add("act", "do_login");

        formBuilder.add("id", token);
        formBuilder.add("pin", pin);

        requestBuilder.url(ChanUrls.getPassUrl());
        requestBuilder.post(formBuilder.build());
    }

    @Override
    public void process(Response response, String result) throws IOException {
        boolean authSuccess = false;
        if (result.contains("Success! Your device is now authorized")) {
            authSuccess = true;
        } else {
            if (result.contains("Your Token must be exactly 10 characters")) {
                message = "Incorrect token";
            } else if (result.contains("You have left one or more fields blank")) {
                message = "You have left one or more fields blank";
            } else if (result.contains("Incorrect Token or PIN")) {
                message = "Incorrect Token or PIN";
            } else {
                message = "Unknown error";
            }
        }

        if (authSuccess) {
            List<String> cookies = response.headers("Set-Cookie");
            String passId = null;
            for (String cookie : cookies) {
                try {
                    List<HttpCookie> parsedList = HttpCookie.parse(cookie);
                    for (HttpCookie parsed : parsedList) {
                        if (parsed.getName().equals("pass_id") && !parsed.getValue().equals("0")) {
                            passId = parsed.getValue();
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (passId != null) {
                this.passId = passId;
                message = "Success! Your device is now authorized.";
                success = true;
            } else {
                message = "Could not get pass id";
            }
        }
    }
}

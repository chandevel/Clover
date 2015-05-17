package org.floens.chan.core.reply;

import android.text.TextUtils;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Reply;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReplyHttpCall extends HttpCall {
    private static final String TAG = "ReplyHttpCall";
    private static final Random RANDOM = new Random();
    private static final Pattern THREAD_NO_PATTERN = Pattern.compile("<!-- thread:([0-9]+),no:([0-9]+) -->");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"errmsg\"[^>]*>(.*?)<\\/span");

    public boolean posted;
    public String errorMessage;
    public String text;
    public String password;
    public int threadNo = -1;
    public int postNo = -1;

    private final Reply reply;

    public ReplyHttpCall(Reply reply) {
        this.reply = reply;
    }

    @Override
    public void setup(Request.Builder requestBuilder) {
        boolean thread = reply.resto >= 0;

        password = Long.toHexString(RANDOM.nextLong());

        MultipartBuilder formBuilder = new MultipartBuilder();
        formBuilder.type(MultipartBuilder.FORM);

        formBuilder.addFormDataPart("mode", "regist");
        formBuilder.addFormDataPart("pwd", password);

        if (thread) {
            formBuilder.addFormDataPart("resto", String.valueOf(reply.resto));
        }

        formBuilder.addFormDataPart("name", reply.name);
        formBuilder.addFormDataPart("email", reply.options);

        if (!thread && !TextUtils.isEmpty(reply.subject)) {
            formBuilder.addFormDataPart("sub", reply.subject);
        }

        formBuilder.addFormDataPart("com", reply.comment);

        if (reply.captchaResponse != null) {
            formBuilder.addFormDataPart("g-recaptcha-response", reply.captchaResponse);
        }

        if (reply.file != null) {
            formBuilder.addFormDataPart("upfile", reply.fileName, RequestBody.create(
                    MediaType.parse("application/octet-stream"), reply.file
            ));
        }

        if (reply.spoilerImage) {
            formBuilder.addFormDataPart("spoiler", "on");
        }

        requestBuilder.url(ChanUrls.getReplyUrl(reply.board));
        requestBuilder.post(formBuilder.build());

        if (reply.usePass) {
            requestBuilder.addHeader("Cookie", "pass_id=" + reply.passId);
        }
    }

    @Override
    public void process(Response response, String result) throws IOException {
        text = result;

        Matcher errorMessageMatcher = ERROR_MESSAGE.matcher(result);
        if (errorMessageMatcher.find()) {
            errorMessage = Jsoup.parse(errorMessageMatcher.group(1)).body().ownText().replace("[]", "");
        } else {
            Matcher threadNoMatcher = THREAD_NO_PATTERN.matcher(result);
            if (threadNoMatcher.find()) {
                try {
                    threadNo = Integer.parseInt(threadNoMatcher.group(1));
                    postNo = Integer.parseInt(threadNoMatcher.group(2));
                } catch (NumberFormatException ignored) {
                }

                if (threadNo >= 0 && postNo >= 0) {
                    posted = true;
                }
            }
        }
    }

    @Override
    public void fail(Request request, IOException e) {
    }
}

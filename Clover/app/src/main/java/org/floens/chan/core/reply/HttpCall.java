package org.floens.chan.core.reply;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.IOUtils;
import org.floens.chan.utils.Logger;

import java.io.IOException;

public abstract class HttpCall implements Callback {
    private static final String TAG = "HttpCall";

    private boolean successful = false;
    private ReplyManager.HttpCallback callback;

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public abstract void setup(Request.Builder requestBuilder);

    public abstract void process(Response response, String result) throws IOException;

    public void fail(Request request, IOException e) {
    }

    @SuppressWarnings("unchecked")
    public void postUI(boolean successful) {
        if (successful) {
            callback.onHttpSuccess(this);
        } else {
            callback.onHttpFail(this);
        }
    }

    @Override
    public void onResponse(Response response) {
        try {
            if (response.isSuccessful() && response.body() != null) {
                String responseString = response.body().string();
                process(response, responseString);
                successful = true;
            } else {
                onFailure(response.request(), null);
            }
        } catch (IOException e) {
            Logger.e(TAG, "IOException processing response", e);
        } finally {
            IOUtils.closeQuietly(response.body());
        }

        AndroidUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                postUI(successful);
            }
        });
    }

    @Override
    public void onFailure(Request request, IOException e) {
        fail(request, e);
    }

    void setCallback(ReplyManager.HttpCallback<? extends HttpCall> callback) {
        this.callback = callback;
    }
}

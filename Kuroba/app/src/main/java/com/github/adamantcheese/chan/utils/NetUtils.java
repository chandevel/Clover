package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.JsonReader;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;
import static java.nio.charset.StandardCharsets.UTF_8;

public class NetUtils {
    private static final String TAG = "NetUtils";

    public static void makeBitmapRequest(
            @NonNull final HttpUrl url, @NonNull final BitmapResult result, final int width, final int height
    ) {
        instance(OkHttpClient.class).newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.e(TAG, "Error loading bitmap from " + url.toString());
                result.onBitmapFailure(BitmapFactory.decodeResource(getRes(), R.drawable.error_icon));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                Bitmap bitmap = null;
                //noinspection ConstantConditions
                try (InputStream inputStream = response.body().byteStream()) {
                    bitmap = BitmapFactory.decodeStream(inputStream);
                } catch (Exception ignored) {
                }
                bitmap = bitmap != null ? Bitmap.createScaledBitmap(bitmap, width, height, true) : null;

                if (bitmap != null) {
                    result.onBitmapSuccess(bitmap);
                } else {
                    result.onBitmapFailure(BitmapFactory.decodeResource(getRes(), R.drawable.error_icon));
                }
                response.close();
            }
        });
    }

    public interface BitmapResult {
        void onBitmapFailure(Bitmap errormap);

        void onBitmapSuccess(Bitmap bitmap);
    }

    public static <T> void makeJsonRequest(
            @NonNull final HttpUrl url, @NonNull final JsonResult<T> result, @NonNull final JsonParser<T> parser
    ) {
        instance(OkHttpClient.class).newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.e(TAG, "Error with request: ", e);
                result.onJsonFailure();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (JsonReader jsonReader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(response.body()
                        .bytes()), UTF_8))) {
                    T read = parser.parse(jsonReader);
                    if (read != null) {
                        result.onJsonSuccess(read);
                    } else {
                        throw new Exception("Json parse returned null object");
                    }
                } catch (Exception e) {
                    Logger.e(TAG, "Error parsing JSON: ", e);
                    result.onJsonFailure();
                }
            }
        });
    }

    public interface JsonResult<T> {
        void onJsonFailure();

        void onJsonSuccess(T result);
    }

    public interface JsonParser<T> {
        T parse(JsonReader reader)
                throws Exception;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static <T> Call makeHTMLRequest(
            @NonNull final HttpUrl url, @NonNull final HTMLResult<T> result, @NonNull final HTMLReader<T> reader
    ) {
        Call call = instance(OkHttpClient.class).newCall(new Request.Builder().url(url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {

            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try (ByteArrayInputStream baos = new ByteArrayInputStream(response.body().bytes())) {
                    Document document = Jsoup.parse(baos, null, url.toString());

                    T read = reader.read(document);
                    result.onHTMLSuccess(read);
                } catch (Exception e) {
                    result.onHTMLFailure(e);
                }
                response.close();
            }
        });
        return call;
    }

    public interface HTMLResult<T> {
        void onHTMLFailure(Exception e);

        void onHTMLSuccess(T result);
    }

    public interface HTMLReader<T> {
        T read(Document document);
    }

}

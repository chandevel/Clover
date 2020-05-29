package com.github.adamantcheese.chan.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

public class NetUtils {
    public static void makeBitmapRequest(@NonNull HttpUrl url, @NonNull BitmapResult result, int width, int height) {
        instance(OkHttpClient.class).newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                Logger.e("NetUtils", "Error loading bitmap from " + url.toString());
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
}

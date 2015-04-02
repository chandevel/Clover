/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.volley.toolbox;

import android.content.Context;
import android.net.http.AndroidHttpClient;
import android.os.Build;

import com.android.volley.Network;
import com.android.volley.RequestQueue;
import com.android.volley.compat.NoSSLv3Compat;

import java.io.File;

public class Volley {

    /** Default on-disk cache directory. */
    public static final String DEFAULT_CACHE_DIR = "volley";

    public static RequestQueue newRequestQueue(Context context, String userAgent, HttpStack stack, File cacheDir, int diskCacheSize) {
        if (stack == null) {
            if (Build.VERSION.SDK_INT >= 9) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                    // Use a socket factory that removes sslv3
                    stack = new HurlStack(userAgent, null, new NoSSLv3Compat.NoSSLv3Factory());
                } else {
                    stack = new HurlStack(userAgent);
                }
            } else {
                // Prior to Gingerbread, HttpUrlConnection was unreliable.
                // See: http://android-developers.blogspot.com/2011/09/androids-http-clients.html
                stack = new HttpClientStack(AndroidHttpClient.newInstance(userAgent));
            }
        }

        Network network = new BasicNetwork(stack);

        DiskBasedCache diskCache = diskCacheSize < 0 ? new DiskBasedCache(cacheDir) : new DiskBasedCache(cacheDir, diskCacheSize);
        RequestQueue queue = new RequestQueue(diskCache, network);
        queue.start();

        return queue;
    }
}

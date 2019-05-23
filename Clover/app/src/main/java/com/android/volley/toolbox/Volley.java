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
            stack = new HurlStack(userAgent);
        }

        Network network = new BasicNetwork(stack);

        DiskBasedCache diskCache = diskCacheSize < 0 ? new DiskBasedCache(cacheDir) : new DiskBasedCache(cacheDir, diskCacheSize);
        RequestQueue queue = new RequestQueue(diskCache, network);
        queue.start();

        return queue;
    }
}

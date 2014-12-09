/**
 * Copyright (C) 2013 The Android Open Source Project
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
package org.floens.chan.ui.view;

import android.content.Context;
import android.util.AttributeSet;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import org.floens.chan.utils.Utils;

public class CustomScaleImageView extends SubsamplingScaleImageView {
    private InitedCallback initCallback;

    public CustomScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
    }

    public CustomScaleImageView(Context context) {
        super(context);
    }

    public void setInitCallback(InitedCallback initCallback) {
        this.initCallback = initCallback;
    }

    @Override
    protected void onImageReady() {
        super.onImageReady();

        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (initCallback != null) {
                    initCallback.onInit();
                }
            }
        });
    }

    @Override
    protected void onOutOfMemory() {
        super.onOutOfMemory();

        Utils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (initCallback != null) {
                    initCallback.onOutOfMemory();
                }
            }
        });
    }

    public interface InitedCallback {
        public void onInit();

        public void onOutOfMemory();
    }
}

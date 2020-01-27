/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
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
package com.github.adamantcheese.chan.core.cache;

import com.github.k1rakishou.fsaf.file.AbstractFile;
import com.github.k1rakishou.fsaf.file.RawFile;

public abstract class FileCacheListener {

    /**
     * Called before the download has been started to prepare for progress updates. This usually
     * happens after we have received a response for the HEAD request that we send before starting
     * downloading anything, which may take up to 1 second. In some cases we won't send the HEAD
     * request (like when the setting to chunk downloads is set to 1 chunk) so this callback will
     * be called immediately.
     */
    public void onStart(int chunksCount) {
    }

    /**
     * In case of the file being downloaded in chunks [chunkIndex] will be representing the chunk
     * index. Otherwise it will always be 0.
     * The amount of chunks is being passed into the [onStart] event.
     */
    public void onProgress(int chunkIndex, long downloaded, long total) {
    }

    /**
     * Called when the file download was completed.
     */
    public void onSuccess(RawFile file) {
    }

    /**
     * This is called when we got 404 status from the server. You must override this method because
     * onFail won't be called!
     */
    public void onNotFound() {
    }

    /**
     * Called when there was an error downloading the file.
     * <b>This is not called when the download was canceled.</b>
     */
    public void onFail(Exception exception) {
    }

    /**
     * Called when the file download was stopped by WebmStreamingSource. Right now this is only used
     * for the WebmStreaming so there is no need to override this. But if you need to stop (not
     * cancel) a download then you probably should override this.
     */
    public void onStop(AbstractFile file) {

    }

    /**
     * Called when the file download was canceled.
     */
    public void onCancel() {
    }

    /**
     * When the download was ended, this is always called, when it failed, succeeded or was
     * canceled/stopped.
     */
    public void onEnd() {
    }
}

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

import com.github.adamantcheese.chan.core.saf.file.RawFile;

public abstract class FileCacheListener {
    public void onProgress(long downloaded, long total) {
    }

    /**
     * Called when the file download was completed.
     */
    public void onSuccess(RawFile file) {
    }

    /**
     * Called when there was an error downloading the file.
     * <b>This is not called when the download was cancelled.</b>
     *
     * @param notFound when it was a http 404 error.
     */
    public void onFail(boolean notFound) {
    }

    /**
     * Called when the file download was cancelled.
     */
    public void onCancel() {
    }

    /**
     * When the download was ended, this is always called, when it failed, succeeded or was
     * cancelled.
     */
    public void onEnd() {
    }
}

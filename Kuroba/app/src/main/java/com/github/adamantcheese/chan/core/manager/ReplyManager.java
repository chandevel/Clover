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
package com.github.adamantcheese.chan.core.manager;

import android.content.Context;

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.http.Reply;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

/**
 * Manages replies.
 */
public class ReplyManager {
    private final Context context;

    private Map<Loadable, Reply> drafts = new HashMap<>();

    @Inject
    public ReplyManager(Context context) {
        this.context = context;
    }

    public Reply getReply(Loadable loadable) {
        Reply reply = drafts.get(loadable);
        if (reply == null) {
            reply = new Reply(loadable);
            drafts.put(loadable, reply);
        }
        return reply;
    }

    public void putReply(Reply reply) {
        // Remove files from all other replies because there can only be one picked_file at the same time.
        // Not doing this would be confusing and cause invalid fileNames.
        if(reply.file != null) {
            for (Map.Entry<Loadable, Reply> entry : drafts.entrySet()) {
                if (!entry.getKey().toString().equals(reply.loadable.toString())) {
                    Reply value = entry.getValue();
                    value.file = null;
                    value.fileName = "";
                }
            }
        }

        drafts.put(reply.loadable, reply);
    }

    public File getPickFile() {
        File cacheFile = new File(context.getCacheDir(), "picked_file");
        try {
            if (!cacheFile.exists()) cacheFile.createNewFile(); //ensure the file exists for writing to
        } catch (Exception ignored) {
        }
        return cacheFile;
    }
}

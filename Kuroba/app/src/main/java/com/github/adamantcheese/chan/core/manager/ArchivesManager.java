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

import android.content.res.AssetManager;
import android.util.JsonReader;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.SiteActions;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.utils.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class ArchivesManager
        implements SiteActions.ArchiveRequestListener {
    private final String TAG = "ArchivesManager";
    private List<Archives> archivesList;

    public ArchivesManager(Site s) {
        //setup the archives list from the internal file, populated when you build the application
        AssetManager assetManager = getAppContext().getAssets();
        try {
            InputStream json = assetManager.open("archives.json");
            JsonReader reader = new JsonReader(new InputStreamReader(json));
            archivesList = parseArchives(reader);
        } catch (Exception e) {
            Logger.d(TAG, "Unable to load/parse internal archives list");
        }
        //request a live version of the archives
        s.actions().archives(this);
    }

    public List<ArchivesLayout.PairForAdapter> domainsForBoard(Board b) {
        List<ArchivesLayout.PairForAdapter> result = new ArrayList<>();
        if (archivesList == null) return result;
        for (Archives a : archivesList) {
            for (String code : a.boards) {
                if (code.equals(b.code)) {
                    result.add(new ArchivesLayout.PairForAdapter(a.name, a.domain));
                    break;
                }
            }
        }
        return result;
    }

    public static List<Archives> parseArchives(JsonReader reader)
            throws Exception {
        List<ArchivesManager.Archives> archives = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            ArchivesManager.Archives a = new ArchivesManager.Archives();

            reader.beginObject();
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "name":
                        a.name = reader.nextString();
                        break;
                    case "domain":
                        a.domain = reader.nextString();
                        break;
                    case "boards":
                        List<String> b = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            b.add(reader.nextString());
                        }
                        reader.endArray();
                        a.boards = b;
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            archives.add(a);
        }
        reader.endArray();

        return archives;
    }

    @Override
    public void onArchivesReceived(List<Archives> archives) {
        archivesList = archives;
    }

    public static class Archives {
        public String name = "";
        public String domain = "";
        public List<String> boards;
    }
}

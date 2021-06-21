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

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.util.JsonReader;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.net.NetUtilsClasses.ResponseResult;
import com.github.adamantcheese.chan.core.site.archives.AyaseArchive;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.archives.FoolFuukaArchive;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.utils.Logger;

import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.HttpUrl;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class ArchivesManager
        implements NetUtilsClasses.Converter<List<ExternalSiteArchive>, JsonReader>,
                   ResponseResult<List<ExternalSiteArchive>> {
    private List<ExternalSiteArchive> archivesList;

    private final Map<String, Class<? extends ExternalSiteArchive>> jsonMapping = new HashMap<>();

    @SuppressLint("StaticFieldLeak")
    private static ArchivesManager instance;

    public static ArchivesManager getInstance() {
        if (instance == null) {
            instance = new ArchivesManager();
        }
        return instance;
    }

    private ArchivesManager() {
        // fuuka does not provide an easy API, and is only used for warosu
        jsonMapping.put("fuuka", null);
        // foolfuuka is currently the de-facto archiver of choice
        jsonMapping.put("foolfuuka", FoolFuukaArchive.class);
        // ayase is a replacement for foolfuuka, but currently has no sites and the implementation throws NotImplementedErrors
        jsonMapping.put("ayase", AyaseArchive.class);

        //setup the archives list from the internal file, populated when you build the application
        AssetManager assetManager = getAppContext().getAssets();
        try {
            // archives.json should only contain FoolFuuka archives, as no other proper archiving software with an API seems to exist
            try (JsonReader reader = new JsonReader(new InputStreamReader(assetManager.open("archives.json")))) {
                archivesList = convert(reader);
            }
        } catch (Exception e) {
            Logger.d(this, "Unable to load/parse internal archives list", e);
        }

        // fresh copy request, in case of updates (infrequent)
        NetUtils.makeJsonRequest(
                HttpUrl.get("https://4chenz.github.io/archives.json/archives.json"),
                this,
                this,
                NetUtilsClasses.ONE_DAY_CACHE
        );
    }

    public List<ExternalSiteArchive> archivesForBoard(Board b) {
        List<ExternalSiteArchive> result = new ArrayList<>();
        if (archivesList == null || !(b.site instanceof Chan4)) return result; //4chan only
        for (ExternalSiteArchive a : archivesList) {
            for (String code : a.boardCodes) {
                if (code.equals(b.code)) {
                    result.add(a);
                    break;
                }
            }
        }
        return result;
    }

    @Nullable
    public ExternalSiteArchive archiveForDomain(@NonNull String domain) {
        for (ExternalSiteArchive a : archivesList) {
            if (a.domain.contains(domain)) return a;
        }
        return null;
    }

    @Override
    public List<ExternalSiteArchive> convert(JsonReader reader)
            throws Exception {
        List<ExternalSiteArchive> archives = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginObject();
            String name = "";
            String domain = "";
            String software = "";
            List<String> boardCodes = Collections.emptyList();
            boolean search = false;
            while (reader.hasNext()) {
                switch (reader.nextName()) {
                    case "name":
                        name = reader.nextString();
                        break;
                    case "domain":
                        domain = reader.nextString();
                        break;
                    case "software":
                        software = reader.nextString();
                        break;
                    case "boards":
                        boardCodes = new ArrayList<>();
                        reader.beginArray();
                        while (reader.hasNext()) {
                            boardCodes.add(reader.nextString());
                        }
                        reader.endArray();
                        break;
                    case "search":
                        search = true;
                        reader.skipValue();
                        break;
                    default:
                        reader.skipValue();
                        break;
                }
            }
            reader.endObject();
            Class<? extends ExternalSiteArchive> archiveClass = jsonMapping.get(software);
            if (archiveClass != null) {
                archives.add(archiveClass.getConstructor(String.class, String.class, List.class, boolean.class)
                        .newInstance(domain, name, boardCodes, search));
            }
        }
        reader.endArray();
        return archives;
    }

    @Override
    public void onFailure(Exception e) {}

    @Override
    public void onSuccess(List<ExternalSiteArchive> result) {
        archivesList = result;
    }
}

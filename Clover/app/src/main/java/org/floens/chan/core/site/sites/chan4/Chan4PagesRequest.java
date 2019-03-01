/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.site.sites.chan4;

import android.util.JsonReader;

import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;

import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.net.JsonReaderRequest;
import org.floens.chan.core.site.Page;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.ThreadTime;

import java.util.ArrayList;
import java.util.List;

public class Chan4PagesRequest extends JsonReaderRequest<List<Page>> {

    public Chan4PagesRequest(Site site, Board board, Listener<List<Page>> listener, ErrorListener errorListener) {
        super(site.endpoints().pages(board).toString(), listener, errorListener);
    }

    @Override
    public List<Page> readJson(JsonReader reader) throws Exception {
        List<Page> pages = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            pages.add(readPageEntry(reader));
        }
        reader.endArray();

        return pages;
    }

    private Page readPageEntry(JsonReader reader) throws Exception {
        int pageNo = -1;
        List<ThreadTime> threadTimes = null;

        reader.beginObject();
        while(reader.hasNext()) {
            String nextName = reader.nextName();
            if(nextName.equals("page")) {
                pageNo = reader.nextInt();
            } else if (nextName.equals("threads")) {
                threadTimes = readThreadTimes(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new Page(pageNo, threadTimes);
    }

    private List<ThreadTime> readThreadTimes(JsonReader reader) throws Exception {
        List<ThreadTime> threadTimes = new ArrayList<>();

        reader.beginArray();
        while(reader.hasNext()) {
            threadTimes.add(readThreadTime(reader));
        }
        reader.endArray();

        return threadTimes;
    }

    private ThreadTime readThreadTime(JsonReader reader) throws Exception {
        int no = -1;
        long modified = -1;

        reader.beginObject();
        while(reader.hasNext()) {
            String nextName = reader.nextName();
            if (nextName.equals("no")) {
                no = reader.nextInt();
            } else if (nextName.equals("last_modified")) {
                modified = reader.nextLong();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new ThreadTime(no, modified);
    }
}

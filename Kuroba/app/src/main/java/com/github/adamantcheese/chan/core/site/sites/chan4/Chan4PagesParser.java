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
package com.github.adamantcheese.chan.core.site.sites.chan4;

import android.util.JsonReader;

import com.github.adamantcheese.chan.core.net.NetUtilsClasses;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.*;

import java.util.ArrayList;
import java.util.List;

public class Chan4PagesParser
        implements NetUtilsClasses.Converter<ChanPages, JsonReader> {
    @Override
    public ChanPages convert(JsonReader reader)
            throws Exception {
        ChanPages pages = new ChanPages();

        reader.beginArray();
        while (reader.hasNext()) {
            pages.add(readPageEntry(reader));
        }
        reader.endArray();

        return pages;
    }

    private ChanPage readPageEntry(JsonReader reader)
            throws Exception {
        int pageNo = -1;
        List<Integer> threadNumbers = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String nextName = reader.nextName();
            if (nextName.equals("page")) {
                pageNo = reader.nextInt();
            } else if (nextName.equals("threads")) {
                threadNumbers = readThreadNumbers(reader);
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return new ChanPage(pageNo, threadNumbers);
    }

    private List<Integer> readThreadNumbers(JsonReader reader)
            throws Exception {
        List<Integer> threadNumbers = new ArrayList<>();

        reader.beginArray();
        while (reader.hasNext()) {
            threadNumbers.add(readOneThreadNumber(reader));
        }
        reader.endArray();

        return threadNumbers;
    }

    private int readOneThreadNumber(JsonReader reader)
            throws Exception {
        int no = -1;

        reader.beginObject();
        while (reader.hasNext()) {
            String nextName = reader.nextName();
            if (nextName.equals("no")) {
                no = reader.nextInt();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();

        return no;
    }
}

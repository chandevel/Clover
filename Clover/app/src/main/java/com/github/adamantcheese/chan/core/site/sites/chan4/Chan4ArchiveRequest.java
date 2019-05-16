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

import com.android.volley.Response;

import com.github.adamantcheese.chan.core.model.Archive;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.net.HtmlReaderRequest;
import com.github.adamantcheese.chan.core.site.Site;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

public class Chan4ArchiveRequest extends HtmlReaderRequest<Archive> {
    public Chan4ArchiveRequest(Site site, Board board,
                               Response.Listener<Archive> listener,
                               Response.ErrorListener errorListener) {
        super(site.endpoints().archive(board).toString(), listener, errorListener);
    }

    @Override
    public Archive readDocument(Document document) {
        List<Archive.ArchiveItem> items = new ArrayList<>();

        Element table = document.getElementById("arc-list");
        Element tableBody = table.getElementsByTag("tbody").first();
        Elements trs = tableBody.getElementsByTag("tr");
        for (Element tr : trs) {
            Elements dataElements = tr.getElementsByTag("td");
            String description = dataElements.get(1).text();
            int id = Integer.parseInt(dataElements.get(0).text());
            items.add(Archive.ArchiveItem.fromDescriptionId(description, id));
        }

        return Archive.fromItems(items);
    }
}

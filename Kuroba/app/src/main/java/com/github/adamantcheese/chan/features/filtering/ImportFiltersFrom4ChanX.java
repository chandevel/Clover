package com.github.adamantcheese.chan.features.filtering;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.ui.helper.RefreshUIMessage.Reason.FILTERS_CHANGED;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getClipboardContent;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;

import android.content.Context;
import android.graphics.Color;

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.core.database.*;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.site.SiteRegistry;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs;
import com.github.adamantcheese.chan.ui.helper.RefreshUIMessage;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

public class ImportFiltersFrom4ChanX {
    /**
     * From 4chanX's documentation<br>
     * <br><br>
     *
     * <div><code>Filter</code> is disabled.</div>
     * <p>
     * Use <a href="https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Regular_Expressions" target="_blank">regular expressions</a>, one per line.<br>
     * Lines starting with a <code>#</code> will be ignored.<br>
     * For example, <code>/weeaboo/i</code> will filter posts containing the string `<code>weeaboo</code>`, case-insensitive.<br>
     * MD5 and Unique ID filtering use exact string matching, not regular expressions.
     * </p>
     * <ul>You can use these settings with each regular expression, separate them with semicolons:
     * <li>
     * Per boards, separate them with commas. It is global if not specified. Use <code>sfw</code> and <code>nsfw</code> to reference all worksafe or not-worksafe boards.<br>
     * For example: <code>boards:a,jp;</code>.<br>
     * To specify boards on a particular site, put the beginning of the domain and a slash character before the list.<br>
     * Any initial <code>www.</code> should not be included, and all 4chan domains are considered <code>4chan.org</code>.<br>
     * For example: <code>boards:4:a,jp,sama:a,z;</code>.<br>
     * An asterisk can be used to specify all boards on a site.<br>
     * For example: <code>boards:4:*;</code>.<br>
     * </li>
     * <li>
     * Select boards to be excluded from the filter. The syntax is the same as for the <code>boards:</code> option above.<br>
     * For example: <code>exclude:vg,v;</code>.
     * </li>
     * <li>
     * Filter OPs only along with their threads (`only`) or replies only (`no`).<br>
     * For example: <code>op:only;</code> or <code>op:no;</code>.
     * </li>
     * <li>
     * Filter only posts with files (`only`) or only posts without files (`no`).<br>
     * For example: <code>file:only;</code> or <code>file:no;</code>.
     * </li>
     * <li>
     * Overrule the `Show Stubs` setting if specified: create a stub (`yes`) or not (`no`).<br>
     * For example: <code>stub:yes;</code> or <code>stub:no;</code>.
     * </li>
     * <li>
     * Highlight instead of hiding. You can specify a class name to use with a userstyle.<br>
     * For example: <code>highlight;</code> or <code>highlight:wallpaper;</code>.
     * </li>
     * <li>
     * Highlighted OPs will have their threads put on top of the board index by default.<br>
     * For example: <code>top:yes;</code> or <code>top:no;</code>.
     * </li>
     * <li>
     * Show a desktop notification instead of hiding.<br>
     * For example: <code>notify;</code>.
     * </li>
     * <li>
     * Filters in the "General" section apply to multiple fields, by default <code>subject,name,filename,comment</code>.<br>
     * The fields can be specified with the <code>type</code> option, separated by commas.<br>
     * For example: <code>type:@{filterTypes};</code>.<br>
     * Types can also be combined with a <code>+</code> sign; this indicates the filter applies to the given fields joined by newlines.<br>
     * For example: <code>type:filename+filesize+dimensions;</code>.<br>
     * </li>
     * </ul>
     */
    public static void import4ChanXFilters(
            Context context,
            DatabaseFilterManager databaseFilterManager,
            RecyclerView.Adapter<?> adapter
    ) {
        int nextItemOrder = adapter.getItemCount();
        CommonDataStructs.Filters createdFilters = new CommonDataStructs.Filters();
        String[] chanXfilters = getClipboardContent().toString().split("\n");
        for (String filterString : chanXfilters) {
            String trimmedFilterString = filterString.trim();
            if (trimmedFilterString.isEmpty()) continue;
            if (StringUtils.startsWithAny(trimmedFilterString, "#", "-")) continue;
            String[] filterAttributes = trimmedFilterString.split(";");
            Filter created = new Filter();
            created.type =
                    FilterType.SUBJECT.flag | FilterType.NAME.flag | FilterType.FILENAME.flag | FilterType.COMMENT.flag;
            created.action = FilterEngine.FilterAction.REMOVE.ordinal();
            created.order = nextItemOrder;
            for (String filterAttribute : filterAttributes) {
                boolean hasColon = filterAttribute.indexOf(':') != -1;
                String attributeEntry =
                        hasColon ? filterAttribute.substring(0, filterAttribute.indexOf(':')) : filterAttribute;
                String attributeValue = hasColon ? filterAttribute.substring(filterAttribute.indexOf(':') + 1) : "";
                switch (attributeEntry) {
                    case "boards":
                        String[] boardEntries = attributeValue.split(",");
                        StringBuilder boardsList = new StringBuilder();
                        for (int i = 0; i < boardEntries.length; i++) {
                            String entry = boardEntries[i];
                            boolean entryHasColon = entry.indexOf(':') != -1;
                            String entrySite =
                                    entryHasColon ? entry.substring(0, filterAttribute.indexOf(':')) : "4chan";
                            String entryBoard =
                                    entryHasColon ? entry.substring(filterAttribute.indexOf(':') + 1) : entry;

                            // don't care to process this wildcard, user will have to manually adjust
                            if ("*".equals(entryBoard)) continue;

                            int siteModelDatabaseId = 1; // default to what 4chan probably is for the user
                            SiteModel model =
                                    DatabaseUtils.runTask(instance(DatabaseSiteManager.class).getForClassID(SiteRegistry.getClassIdForSiteName(
                                            entrySite)));
                            if (model != null) {
                                siteModelDatabaseId = model.id;
                            }

                            boardsList.append("").append(siteModelDatabaseId);
                            boardsList.append(":");
                            boardsList.append(entryBoard);
                            if (i + 1 != boardEntries.length) {
                                boardsList.append(",");
                            }
                        }
                        created.boards = boardsList.toString();
                        created.allBoards = false;
                        break;
                    case "exclude":
                    case "file":
                    case "im":
                        // unsupported, ignore
                        break;
                    case "op":
                        created.onlyOnOP = "only".equals(attributeValue);
                        break;
                    case "stub":
                        if ("yes".equals(attributeValue)) {
                            created.action = FilterEngine.FilterAction.HIDE.ordinal();
                        }
                        break;
                    case "highlight":
                        created.action = FilterEngine.FilterAction.COLOR.ordinal();
                        created.color = Color.RED;
                        break;
                    case "top":
                    case "notify":
                        created.action = FilterEngine.FilterAction.WATCH.ordinal();
                        break;
                    case "type":
                        String[] types = attributeValue.split("[,+]]");
                        int newType = 0;
                        for (String type : types) {
                            switch (type) {
                                case "name":
                                    newType |= FilterType.NAME.flag;
                                    break;
                                case "uniqueID":
                                    newType |= FilterType.ID.flag;
                                    break;
                                case "tripcode":
                                    newType |= FilterType.TRIPCODE.flag;
                                    break;
                                case "subject":
                                    newType |= FilterType.SUBJECT.flag;
                                    break;
                                case "comment":
                                    newType |= FilterType.COMMENT.flag;
                                    break;
                                case "flag":
                                    newType |= FilterType.FLAG_CODE.flag;
                                    break;
                                case "filename":
                                    newType |= FilterType.FILENAME.flag;
                                    break;
                                case "MD5":
                                    newType |= FilterType.IMAGE_HASH.flag;
                                    break;
                                case "capcode":
                                case "pass":
                                case "email":
                                case "dimensions":
                                case "filesize":
                                default:
                                    // unsupported
                                    break;
                            }
                        }
                        created.type = newType;
                        break;
                    default:
                        // none of these have special processing, but are here so you are aware
                        // no entry, probably regex, MD5 hash, or an ID
                        if (attributeEntry.charAt(0) == '/') {
                            // regex
                            created.pattern = attributeEntry;
                        } else if (attributeEntry.length() == 32) {
                            // probably MD5
                            created.pattern = "/" + attributeEntry + "/";
                        } else {
                            // probably an ID?
                            created.pattern = "/" + attributeEntry + "/";
                        }
                        break;
                }
            }
            createdFilters.add(created);
            nextItemOrder++;
        }
        DatabaseUtils.runTask(databaseFilterManager.createFilters(createdFilters));
        BackgroundUtils.runOnMainThread(() -> {
            showToast(context, "Finished importing filters!");
            postToEventBus(new RefreshUIMessage(FILTERS_CHANGED));
        });
    }
}

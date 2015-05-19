package org.floens.chan.chan;

import android.net.Uri;

import org.floens.chan.Chan;
import org.floens.chan.core.model.Loadable;

import java.util.List;

public class ChanHelper {
    public static Loadable getLoadableFromStartUri(Uri uri) {
        Loadable loadable = null;
        int markedNo = -1;

        List<String> parts = uri.getPathSegments();

        if (parts.size() > 0) {
            String rawBoard = parts.get(0);
            if (Chan.getBoardManager().getBoardExists(rawBoard)) {
                if (parts.size() == 1) {
                    // Board mode
                    loadable = new Loadable(rawBoard);
                } else if (parts.size() >= 3) {
                    // Thread mode
                    int no = -1;

                    try {
                        no = Integer.parseInt(parts.get(2));
                    } catch (NumberFormatException ignored) {
                    }

                    int post = -1;
                    String fragment = uri.getFragment();
                    if (fragment != null) {
                        int index = fragment.indexOf("p");
                        if (index >= 0) {
                            try {
                                post = Integer.parseInt(fragment.substring(index + 1));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }

                    if (no >= 0) {
                        loadable = new Loadable(rawBoard, no);
                        if (post >= 0) {
                            loadable.markedNo = post;
                        }
                    }
                }
            }
        }

        return loadable;
    }
}

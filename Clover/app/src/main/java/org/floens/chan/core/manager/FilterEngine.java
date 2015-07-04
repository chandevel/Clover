package org.floens.chan.core.manager;

import org.floens.chan.core.model.Filter;

import java.util.ArrayList;
import java.util.List;

public class FilterEngine {
    public enum FilterType {
        TRIPCODE(0),
        NAME(1),
        COMMENT(2),
        ID(3),
        SUBJECT(4),
        FILENAME(5);

        public final int id;

        FilterType(int id) {
            this.id = id;
        }

        public static FilterType forId(int id) {
            for (FilterType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    private static final FilterEngine instance = new FilterEngine();

    public static FilterEngine getInstance() {
        return instance;
    }

    private List<Filter> filters = new ArrayList<>();

    public FilterEngine() {

    }

    public void add(Filter filter) {
    }
}

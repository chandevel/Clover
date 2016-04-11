package org.floens.chan.core.manager;

import java.util.ArrayList;
import java.util.List;

public enum FilterType {
    TRIPCODE(0x1, false),
    NAME(0x2, false),
    COMMENT(0x4, true),
    ID(0x8, false),
    SUBJECT(0x10, true),
    FILENAME(0x20, true);

    public final int flag;
    public final boolean isRegex;

    FilterType(int flag, boolean isRegex) {
        this.flag = flag;
        this.isRegex = isRegex;
    }

    public static List<FilterType> forFlags(int flag) {
        List<FilterType> enabledTypes = new ArrayList<>();
        for (FilterType filterType : values()) {
            if ((filterType.flag & flag) != 0) {
                enabledTypes.add(filterType);
            }
        }
        return enabledTypes;
    }
}

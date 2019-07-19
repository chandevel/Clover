package com.github.adamantcheese.chan.core.mapper;

import com.github.adamantcheese.chan.core.model.save.SerializableSite;
import com.github.adamantcheese.chan.core.site.Site;

public class SiteMapper {

    public static SerializableSite toSerializableSite(Site site) {
        return new SerializableSite(
                site.id()
        );
    }

}

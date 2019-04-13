package org.floens.chan.core.di;

import org.codejargon.feather.Provides;
import org.floens.chan.core.database.LoadableProvider;
import org.floens.chan.core.repository.SiteRepository;
import org.floens.chan.core.site.SiteResolver;
import org.floens.chan.core.site.SiteService;

import javax.inject.Singleton;

public class SiteModule {

    @Provides
    @Singleton
    public SiteResolver provideSiteResolver(
            SiteRepository siteRepository,
            LoadableProvider loadableProvider
    ) {
        return new SiteResolver(siteRepository, loadableProvider);
    }

    @Provides
    @Singleton
    public SiteService provideSiteService(
            SiteRepository siteRepository,
            SiteResolver siteResolver
    ) {
        return new SiteService(siteRepository, siteResolver);
    }
}

package org.floens.chan.core.di;

import android.content.Context;

import org.floens.chan.core.UserAgentProvider;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class AppModule {
    private Context applicationContext;
    private UserAgentProvider userAgentProvider;

    public AppModule(Context applicationContext, UserAgentProvider userAgentProvider) {
        this.applicationContext = applicationContext;
        this.userAgentProvider = userAgentProvider;
    }

    @Provides
    @Singleton
    public Context provideApplicationContext() {
        return applicationContext;
    }

    @Provides
    @Singleton
    public UserAgentProvider provideUserAgentProvider() {
        return userAgentProvider;
    }
}

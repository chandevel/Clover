package org.floens.chan.core.di;

import android.content.Context;

import org.codejargon.feather.Provides;
import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.database.LoadableProvider;

import javax.inject.Singleton;

public class DatabaseModule {

    @Provides
    @Singleton
    public DatabaseHelper provideDatabaseHelper(Context applicationContext) {
        return new DatabaseHelper(applicationContext);
    }

    @Provides
    @Singleton
    public DatabaseManager provideDatabaseManager(
            Context applicationContext,
            DatabaseHelper databaseHelper
    ) {
        return new DatabaseManager(applicationContext, databaseHelper);
    }

    @Provides
    @Singleton
    public LoadableProvider provideLoadableProvider(
            DatabaseManager databaseManager
    ) {
        return new LoadableProvider(databaseManager);
    }
}

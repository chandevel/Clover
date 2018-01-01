package org.floens.chan.core.di;

import android.content.Context;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.ChanApplication;
import org.floens.chan.core.site.loader.ChanLoader;
import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.database.LoadableProvider;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.net.BitmapLruImageCache;
import org.floens.chan.core.presenter.BoardSetupPresenter;
import org.floens.chan.core.presenter.ImageViewerPresenter;
import org.floens.chan.core.presenter.ReplyPresenter;
import org.floens.chan.core.presenter.SitesSetupPresenter;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.receiver.WatchUpdateReceiver;
import org.floens.chan.core.saver.ImageSaveTask;
import org.floens.chan.core.site.SiteManager;
import org.floens.chan.core.site.common.ChanReaderRequest;
import org.floens.chan.core.site.http.HttpCallManager;
import org.floens.chan.core.update.UpdateManager;
import org.floens.chan.ui.activity.BoardActivity;
import org.floens.chan.ui.adapter.DrawerAdapter;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.controller.BoardSetupController;
import org.floens.chan.ui.controller.BrowseController;
import org.floens.chan.ui.controller.DeveloperSettingsController;
import org.floens.chan.ui.controller.DrawerController;
import org.floens.chan.ui.controller.FiltersController;
import org.floens.chan.ui.controller.HistoryController;
import org.floens.chan.ui.controller.ImageViewerController;
import org.floens.chan.ui.controller.MainSettingsController;
import org.floens.chan.ui.controller.SiteSetupController;
import org.floens.chan.ui.controller.SitesSetupController;
import org.floens.chan.ui.controller.ViewThreadController;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.layout.FilterLayout;
import org.floens.chan.ui.layout.ReplyLayout;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.service.WatchNotifier;
import org.floens.chan.ui.view.MultiImageView;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module(
        injects = {
//                Context.class, // ApplicationContext

                BoardManager.class,
                DatabaseManager.class,
                ReplyManager.class,
                ImageLoader.class,
                FileCache.class,
                HttpCallManager.class,
                LoadableProvider.class,

                ChanApplication.class,
                MainSettingsController.class,
                ReplyPresenter.class,
                ChanReaderRequest.class,
                ThreadLayout.class,
                DeveloperSettingsController.class,
                BoardActivity.class,
                ThreadPresenter.class,
                FilterEngine.class,
                BrowseController.class,
                FilterLayout.class,
                HistoryController.class,
                DrawerController.class,
                DrawerAdapter.class,
                WatchNotifier.class,
                WatchUpdateReceiver.class,
                ImagePickDelegate.class,
                FiltersController.class,
                PostsFilter.class,
                ChanLoader.class,
                ImageViewerController.class,
                ImageViewerPresenter.class,
                MultiImageView.class,
                ImageSaveTask.class,
                ViewThreadController.class,
                WatchManager.PinWatcher.class,
                UpdateManager.class,
                SiteManager.class,
                SitesSetupPresenter.class,
                BoardSetupPresenter.class,
                SiteSetupController.class,
                SitesSetupController.class,
                BoardSetupController.class,
                ReplyLayout.class,
        },
        complete = false,
        includes = NetModule.class
)
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

    @Provides
    @Singleton
    public ImageLoader provideImageLoader(RequestQueue requestQueue) {
        final int runtimeMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int lruImageCacheSize = runtimeMemory / 8;
        return new ImageLoader(requestQueue, new BitmapLruImageCache(lruImageCacheSize));
    }
}

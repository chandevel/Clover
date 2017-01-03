package org.floens.chan.core.di;

import com.android.volley.RequestQueue;
import com.android.volley.toolbox.ImageLoader;

import org.floens.chan.Chan;
import org.floens.chan.chan.ChanLoader;
import org.floens.chan.chan.ChanParser;
import org.floens.chan.core.cache.FileCache;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.http.ReplyManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.site.loaders.Chan4ReaderRequest;
import org.floens.chan.core.presenter.ImageViewerPresenter;
import org.floens.chan.core.presenter.ReplyPresenter;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.receiver.WatchUpdateReceiver;
import org.floens.chan.core.saver.ImageSaveTask;
import org.floens.chan.ui.activity.StartActivity;
import org.floens.chan.ui.adapter.DrawerAdapter;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.controller.BoardEditController;
import org.floens.chan.ui.controller.BrowseController;
import org.floens.chan.ui.controller.DeveloperSettingsController;
import org.floens.chan.ui.controller.DrawerController;
import org.floens.chan.ui.controller.FiltersController;
import org.floens.chan.ui.controller.HistoryController;
import org.floens.chan.ui.controller.ImageViewerController;
import org.floens.chan.ui.controller.MainSettingsController;
import org.floens.chan.ui.controller.PassSettingsController;
import org.floens.chan.ui.controller.ViewThreadController;
import org.floens.chan.ui.helper.ImagePickDelegate;
import org.floens.chan.ui.layout.FilterLayout;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.service.WatchNotifier;
import org.floens.chan.ui.view.MultiImageView;

import javax.inject.Singleton;

import dagger.Component;

@Component(modules = {
        AppModule.class,
        NetModule.class
})
@Singleton
public interface ChanGraph {
    ChanParser getChanParser();

    BoardManager getBoardManager();

    DatabaseManager getDatabaseManager();

    ReplyManager getReplyManager();

    RequestQueue getRequestQueue();

    ImageLoader getImageLoader();

    FileCache getFileCache();

    void inject(Chan chan);

    void inject(MainSettingsController mainSettingsController);

    void inject(ReplyPresenter replyPresenter);

    void inject(Chan4ReaderRequest chanReaderRequest);

    void inject(ThreadLayout threadLayout);

    void inject(DeveloperSettingsController developerSettingsController);

    void inject(StartActivity startActivity);

    void inject(ThreadPresenter threadPresenter);

    void inject(BoardEditController boardEditController);

    void inject(FilterEngine filterEngine);

    void inject(BrowseController browseController);

    void inject(FilterLayout filterLayout);

    void inject(HistoryController historyController);

    void inject(DrawerController drawerController);

    void inject(DrawerAdapter drawerAdapter);

    void inject(WatchNotifier watchNotifier);

    void inject(WatchUpdateReceiver watchUpdateReceiver);

    void inject(ImagePickDelegate imagePickDelegate);

    void inject(PassSettingsController passSettingsController);

    void inject(FiltersController filtersController);

    void inject(PostsFilter postsFilter);

    void inject(ChanLoader chanLoader);

    void inject(ImageViewerController imageViewerController);

    void inject(ImageViewerPresenter imageViewerPresenter);

    void inject(MultiImageView multiImageView);

    void inject(ImageSaveTask imageSaveTask);

    void inject(ViewThreadController viewThreadController);

    void inject(WatchManager.PinWatcher pinWatcher);
}

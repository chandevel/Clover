package org.floens.chan.core.di;

import org.floens.chan.Chan;
import org.floens.chan.chan.ChanParser;
import org.floens.chan.core.net.ChanReaderRequest;
import org.floens.chan.core.presenter.ReplyPresenter;
import org.floens.chan.ui.controller.DeveloperSettingsController;
import org.floens.chan.ui.controller.MainSettingsController;
import org.floens.chan.ui.layout.ThreadLayout;

import javax.inject.Singleton;

import dagger.Component;

@Component(modules = {
        AppModule.class
})
@Singleton
public interface ChanGraph {
    void inject(Chan chan);

    void inject(MainSettingsController mainSettingsController);

    void inject(ReplyPresenter replyPresenter);

    void inject(ChanReaderRequest chanReaderRequest);

    void inject(ThreadLayout threadLayout);

    void inject(DeveloperSettingsController developerSettingsController);

    ChanParser getChanParser();
}

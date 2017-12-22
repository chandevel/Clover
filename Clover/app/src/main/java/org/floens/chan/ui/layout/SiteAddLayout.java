package org.floens.chan.ui.layout;

import android.content.Context;
import android.support.constraint.ConstraintLayout;
import android.util.AttributeSet;
import android.widget.EditText;

import org.floens.chan.R;
import org.floens.chan.core.presenter.SitesSetupPresenter;

public class SiteAddLayout extends ConstraintLayout {
    private EditText url;
    private SitesSetupPresenter presenter;

    public SiteAddLayout(Context context) {
        this(context, null);
    }

    public SiteAddLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SiteAddLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        url = findViewById(R.id.url);
    }

    public void setPresenter(SitesSetupPresenter presenter) {
        this.presenter = presenter;
    }

    public void onPositiveClicked() {
        presenter.onAddClicked(url.getText().toString());
    }
}

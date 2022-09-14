package com.github.adamantcheese.chan.ui.layout;

import static com.github.adamantcheese.chan.Chan.inject;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;

public class ArchivesLayout
        extends LinearLayout {
    private Callback callback;
    private ArrayAdapter<ExternalSiteArchive> adapter;
    private Loadable op;
    private int postNo = -1;
    private AlertDialog alertDialog;

    public ArchivesLayout(Context context) {
        this(context, null);
    }

    public ArchivesLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArchivesLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inject(this);
        op = Loadable.dummyLoadable();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        adapter = new ArrayAdapter<>(getContext(), R.layout.simple_list_item);
        ((ListView) findViewById(R.id.archives_list)).setAdapter(adapter);
        ((ListView) findViewById(R.id.archives_list)).setOnItemClickListener((parent, view, position, id) -> {
            callback.openArchive((ExternalSiteArchive) parent.getItemAtPosition(position), op, postNo);
            if (alertDialog != null) alertDialog.dismiss();
        });
    }

    public boolean setLoadable(@NonNull Loadable op) {
        this.op = op;
        adapter.addAll(ArchivesManager.getInstance().archivesForBoard(op.board));
        return !adapter.isEmpty();
    }

    public void setCallback(Callback c) {
        callback = c;
    }

    public void setPostNo(int postNo) {
        this.postNo = postNo;
    }

    public void attachToDialog(AlertDialog alertDialog) {
        this.alertDialog = alertDialog;
    }

    public interface Callback {
        void openArchive(ExternalSiteArchive externalSiteArchive, Loadable op, int postNo);
    }
}

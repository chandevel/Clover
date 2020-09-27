package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.orm.Board;
import com.github.adamantcheese.chan.core.site.FoolFuukaArchive;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class ArchivesLayout
        extends LinearLayout {
    private Callback callback;
    private ArrayAdapter<FoolFuukaArchive> adapter;
    private String boardCode;
    private int opNo;
    private AlertDialog alertDialog;

    @Inject
    ArchivesManager archivesManager;

    public ArchivesLayout(Context context) {
        this(context, null);
    }

    public ArchivesLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ArchivesLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        inject(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
        ((ListView) findViewById(R.id.archives_list)).setAdapter(adapter);
        ((ListView) findViewById(R.id.archives_list)).setOnItemClickListener((parent, view, position, id) -> {
            callback.openArchive((FoolFuukaArchive) parent.getItemAtPosition(position), boardCode, opNo);
            if (alertDialog != null) alertDialog.dismiss();
        });
    }

    public boolean setBoard(Board b) {
        boardCode = b.code;
        adapter.addAll(archivesManager.archivesForBoard(b));
        return !adapter.isEmpty();
    }

    public void setCallback(Callback c) {
        callback = c;
    }

    public void setOpNo(int opNo) {
        this.opNo = opNo;
    }

    public void attachToDialog(AlertDialog alertDialog) {
        this.alertDialog = alertDialog;
    }

    public interface Callback {
        void openArchive(FoolFuukaArchive archive, String boardCode, int opNo);
    }
}

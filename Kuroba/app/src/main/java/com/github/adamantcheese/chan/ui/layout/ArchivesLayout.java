package com.github.adamantcheese.chan.ui.layout;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.orm.Board;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class ArchivesLayout
        extends LinearLayout {
    private Callback callback;
    private ArrayAdapter<PairForAdapter> adapter;

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
        ((ListView) findViewById(R.id.archives_list)).setOnItemClickListener((parent, view, position, id) -> callback.openArchive(
                (PairForAdapter) parent.getItemAtPosition(position)));
    }

    public void setBoard(Board b) {
        adapter.addAll(archivesManager.domainsForBoard(b));
    }

    public void setCallback(Callback c) {
        callback = c;
    }

    public static class PairForAdapter
            extends Pair<String, String> {
        public PairForAdapter(@Nullable String first, @Nullable String second) {
            super(first, second);
        }

        @Override
        public String toString() {
            return String.valueOf(first);
        }
    }

    public interface Callback {
        void openArchive(Pair<String, String> domainNamePair);
    }
}

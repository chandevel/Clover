package org.floens.chan.ui.controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.storage.Storage;

import javax.inject.Inject;

import static org.floens.chan.Chan.inject;

public class StorageSetupController extends Controller implements View.OnClickListener {
    private static final int OPEN_TREE_INTENT_RESULT_ID = 101;

    private static final String TAG = "StorageSetupController";

    @Inject
    private Storage storage;

    private TextView text;
    private Button button;

    public StorageSetupController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        inject(this);

        // Navigation
        navigation.setTitle(R.string.storage_setup_screen);

        // View inflation
        view = inflateRes(R.layout.controller_storage_setup);

        // View binding
        text = view.findViewById(R.id.text);
        button = view.findViewById(R.id.button);

        // View setup
        button.setOnClickListener(this);

        updateName();
    }

    @Override
    public void onClick(View v) {
        if (v == button) {
            requestTree();
        }
    }

    private void requestTree() {
        Intent i = storage.getOpenTreeIntent();
        ((Activity) context).startActivityForResult(i, OPEN_TREE_INTENT_RESULT_ID);
        updateName();
    }

    private void updateName() {
        text.setText(storage.currentStorageName());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OPEN_TREE_INTENT_RESULT_ID && resultCode == Activity.RESULT_OK) {
            storage.handleOpenTreeIntent(data.getData());
            updateName();
        }
    }
}

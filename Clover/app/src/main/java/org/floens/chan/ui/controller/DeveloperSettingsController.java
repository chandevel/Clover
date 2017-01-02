/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.controller;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.SavedReply;

import java.util.Random;

import javax.inject.Inject;

import static org.floens.chan.Chan.getGraph;
import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class DeveloperSettingsController extends Controller {
    private TextView summaryText;

    @Inject
    DatabaseManager databaseManager;

    public DeveloperSettingsController(Context context) {
        super(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        getGraph().inject(this);

        navigationItem.setTitle(R.string.settings_developer);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        Button crashButton = new Button(context);

        crashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                throw new RuntimeException("Debug crash");
            }
        });
        crashButton.setText("Crash the app");

        wrapper.addView(crashButton);

        summaryText = new TextView(context);
        summaryText.setPadding(0, dp(25), 0, 0);
        wrapper.addView(summaryText);

        setDbSummary();

        Button resetDbButton = new Button(context);
        resetDbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                databaseManager.reset();
                System.exit(0);
            }
        });
        resetDbButton.setText("Delete database");
        wrapper.addView(resetDbButton);

        Button savedReplyDummyAdd = new Button(context);
        savedReplyDummyAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Random r = new Random();
                int j = 0;
                for (int i = 0; i < 100; i++) {
                    j += r.nextInt(10000);

                    SavedReply saved = new SavedReply("g", j, "");
                    databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager().saveReply(saved));
                }
                setDbSummary();
            }
        });
        savedReplyDummyAdd.setText("Add test rows to savedReply");
        wrapper.addView(savedReplyDummyAdd);

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(wrapper);
        view = scrollView;
        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));
    }

    private void setDbSummary() {
        String dbSummary = "";
        dbSummary += "Database summary:\n";
        dbSummary += databaseManager.getSummary();
        summaryText.setText(dbSummary);
    }
}

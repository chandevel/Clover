/*
 * Chan - 4chan browser https://github.com/Floens/Chan/
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
package org.floens.chan.ui.activity;

import org.floens.chan.ChanApplication;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DeveloperActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        Button crashButton = new Button(this);

        crashButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                @SuppressWarnings("unused")
                int i = 1 / 0;
            }
        });
        crashButton.setText("Crash the app");

        wrapper.addView(crashButton);

        String dbSummary = "";

        dbSummary += "Database summary:\n";
        dbSummary += ChanApplication.getDatabaseManager().getSummary();

        TextView db = new TextView(this);
        db.setPadding(0, 25, 0, 0);
        db.setText(dbSummary);
        wrapper.addView(db);

        Button resetDbButton = new Button(this);
        resetDbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChanApplication.getDatabaseManager().reset();
                System.exit(0);
            }
        });
        resetDbButton.setText("Delete database");
        wrapper.addView(resetDbButton);

        setContentView(wrapper);
    }
}

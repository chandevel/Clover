package org.floens.chan.activity;

import org.floens.chan.database.DatabaseManager;

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
        dbSummary += DatabaseManager.getInstance().getSummary();
        
        TextView db = new TextView(this);
        db.setPadding(0, 25, 0, 0);
        db.setText(dbSummary);
        wrapper.addView(db);
        
        Button resetDbButton = new Button(this);
        resetDbButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatabaseManager.getInstance().reset();
                System.exit(0);
            }
        });
        resetDbButton.setText("Delete database");
        wrapper.addView(resetDbButton);
        
        setContentView(wrapper);
    }
}






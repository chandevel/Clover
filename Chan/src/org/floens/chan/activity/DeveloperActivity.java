package org.floens.chan.activity;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class DeveloperActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout wrapper = new LinearLayout(this);
        
        Button button = new Button(this);
        
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                @SuppressWarnings("unused")
                int i = 1 / 0;
            }
        });
        button.setText("Crash the app");
        
        wrapper.addView(button);
        
        setContentView(wrapper);
    }
}

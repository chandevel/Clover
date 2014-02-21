package org.floens.chan.activity;

import org.floens.chan.fragment.ReplyFragment;
import org.floens.chan.fragment.ThreadFragment;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

public class ReplyActivity extends Activity {
	private static ThreadFragment threadFragment;
	
	public static void setThreadFragment(ThreadFragment tf) {
		threadFragment = tf;
	}
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (threadFragment != null) {
        	getActionBar().setDisplayHomeAsUpEnabled(true);
        	
        	FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, ReplyFragment.newInstance(threadFragment));
            ft.commitAllowingStateLoss();
        	
            threadFragment = null;
        } else {
        	Log.e("Chan", "ThreadFragment was null, exiting!");
        	finish();
        }
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch(item.getItemId()) {
	    case android.R.id.home:
	    	finish();
	    	
	    	return true;
	    }
	    
	    return super.onOptionsItemSelected(item);
	}
}

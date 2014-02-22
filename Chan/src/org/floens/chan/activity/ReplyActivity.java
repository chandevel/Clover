package org.floens.chan.activity;

import org.floens.chan.entity.Loadable;
import org.floens.chan.fragment.ReplyFragment;

import android.app.Activity;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

public class ReplyActivity extends Activity {
	private static Loadable loadable;
	
	public static void setLoadable(Loadable l) {
		loadable = l;
	}
	
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (loadable != null) {
        	getActionBar().setDisplayHomeAsUpEnabled(true);
        	
        	FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, ReplyFragment.newInstance(loadable));
            ft.commitAllowingStateLoss();
        	
            loadable = null;
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

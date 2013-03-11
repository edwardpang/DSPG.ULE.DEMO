package com.dspg.ule.demo;

import android.os.Bundle;
import android.app.Activity;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";
	
	private TextView mTextViewVersionName;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mTextViewVersionName = (TextView) findViewById(R.id.textViewVersionName);
		mTextViewVersionName.setText (getVersionName());
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private String getVersionName () {
		String retval;
		
		try
		{
		    retval = this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
		}
		catch (NameNotFoundException e)
		{
		    Log.v(TAG, e.getMessage());
		    retval = "Unknown";
		    
		}
		return retval;
	}
}

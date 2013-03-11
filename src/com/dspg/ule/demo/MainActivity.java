package com.dspg.ule.demo;

import android.os.Bundle;
import android.app.Activity;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.view.Menu;

import android.widget.LinearLayout;
//import android.widget.ScrollView;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";
	
	private TextView mTextViewVersionName;
	//private ScrollView mScrollViewHanDeviceTable;
	private LinearLayout mScrollViewHanDeviceTableLayout;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mTextViewVersionName = (TextView) findViewById(R.id.textViewVersionName);
		mTextViewVersionName.setText (getVersionName());
		
		//mScrollViewHanDeviceTable = (ScrollView) findViewById(R.id.scrollViewHanDeviceTable);
	    mScrollViewHanDeviceTableLayout = (LinearLayout) findViewById (R.id.scrollViewHanDeviceTableLayout);

	    updateHanDeviceTable (5);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void updateHanDeviceTable (int n) {
		for(int i = 0; i < n; i ++) {
			TableRow row = new TableRow(this);
			TextView tv1 = new TextView(this);
			tv1.setText("#"+i);
			
			TextView tv2 = new TextView(this);
			tv2.setText("Something");

			row.addView(tv1);
			row.addView(tv2);
			
			mScrollViewHanDeviceTableLayout.addView(row);
		}
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

package com.dspg.ule.demo;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dspg.ule.driver.UsbSerialDriver;
import com.dspg.ule.driver.UsbSerialProber;
import com.dspg.ule.util.Debug;
import com.dspg.ule.util.HexDump;
import com.dspg.ule.util.SerialInputOutputManager;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.UsbManager;
import android.view.Menu;

import android.widget.LinearLayout;
//import android.widget.ScrollView;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";
	
	private TextView mTextViewVersionName;
	private TextView mTextViewCmbsConnectedAns;
	
	//private ScrollView mScrollViewHanDeviceTable;
	private LinearLayout mScrollViewHanDeviceTableLayout;

	private UsbManager mUsbManager;
	private UsbSerialDriver mSerialDevice;
	
	private SerialInputOutputManager mSerialIoManager;
	private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
	
    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Debug.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
        	MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                	MainActivity.this.updateReceivedData(data);
                }
            });
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mTextViewCmbsConnectedAns = (TextView) findViewById(R.id.textViewCmbsConnectedAns);
		mTextViewVersionName = (TextView) findViewById(R.id.textViewVersionName);
		mTextViewVersionName.setText (getVersionName());
		
		//mScrollViewHanDeviceTable = (ScrollView) findViewById(R.id.scrollViewHanDeviceTable);
	    mScrollViewHanDeviceTableLayout = (LinearLayout) findViewById (R.id.scrollViewHanDeviceTableLayout);

	    updateHanDeviceTable (5);
	    
	    mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (mSerialDevice != null) {
            try {
                mSerialDevice.close();
            } catch (IOException e) {
                // Ignore.
            }
            mSerialDevice = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSerialDevice = UsbSerialProber.acquire(mUsbManager);
        Debug.d(TAG, "Resumed, mSerialDevice=" + mSerialDevice);
        if (mSerialDevice == null) {
            Debug.i(TAG, "No serial device.");
            mTextViewCmbsConnectedAns.setText(R.string.no);
        } else {
            try {
                mSerialDevice.open();
                mTextViewCmbsConnectedAns.setText(R.string.yes);
            } catch (IOException e) {
                Debug.e(TAG, "Error setting up device: " + e.getMessage());
                mTextViewCmbsConnectedAns.setText(R.string.no);
                try {
                    mSerialDevice.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                mSerialDevice = null;
                return;
            }
            Debug.d(TAG, "Serial device: " + mSerialDevice);
        }
        onDeviceStateChange();
    }
 
	// IO Manager Section
    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Debug.d(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (mSerialDevice != null) {
            Debug.d(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(mSerialDevice, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }
    
    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: " + HexDump.dumpHexString(data);
        	Debug.d(TAG, message);
//			mDumpTextView.append(message);
//        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
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
		    Debug.e(TAG, e.getMessage());
		    retval = "Unknown";
		    
		}
		return retval;
	}
}

package com.dspg.ule.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dspg.ule.driver.UsbSerialDriver;
import com.dspg.ule.driver.UsbSerialProber;
import com.dspg.ule.util.Debug;
import com.dspg.ule.util.HexDump;
import com.dspg.ule.util.SerialInputOutputManager;
import com.dspg.ule.cmbs.RawData;
import com.dspg.ule.cmbs.State;

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
	private TextView mTextViewHanConnectedDeviceAns;
	private State mState;
	private int mTamperCnt;
	private int mAlertCnt;
	private int mHanDeviceCnt;
	
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
		mTextViewHanConnectedDeviceAns = (TextView) findViewById(R.id.textViewHanConnectedDeviceAns);
		mTextViewVersionName = (TextView) findViewById(R.id.textViewVersionName);
		mTextViewVersionName.setText (getVersionName());
		
		//mScrollViewHanDeviceTable = (ScrollView) findViewById(R.id.scrollViewHanDeviceTable);
	    mScrollViewHanDeviceTableLayout = (LinearLayout) findViewById (R.id.scrollViewHanDeviceTableLayout);

	    
	    mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
	    mState = State.START;
	    mTamperCnt = 0;
	    mAlertCnt = 0;
	    mHanDeviceCnt = 1;
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
            
    	    StateMachine ( );
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
        	
        	if (Arrays.equals(data, RawData.CMBS_CMD_HELLO_RPLY))
        		mState = State.CMBS_CMD_HELLO_RPLY;
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_SYS_START_RES))
        		mState = State.CMBS_EV_DSR_SYS_START_RES;
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MNGR_INIT_RES))
        		mState = State.CMBS_EV_DSR_HAN_MNGR_INIT_RES;
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MNGR_START_RES))
        		mState = State.CMBS_EV_DSR_HAN_MNGR_START_RES;        	
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_PARAM_AREA_SET_RES))
        		mState = State.CMBS_EV_DSR_PARAM_AREA_SET_RES;           	
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MSG_RECV_REGISTER_RES))
        		mState = State.CMBS_EV_DSR_HAN_MSG_RECV_REGISTER_RES;
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MSG_RECV_TAMPER)) {
        		Debug.d (TAG, "TAMPER!!!");
        		mTamperCnt ++;
        		mState = State.IDLE;
        	}
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MSG_RECV_ALERT)) {
        		Debug.d (TAG, "ALERT!!!");
        		mAlertCnt ++;
        		mState = State.IDLE;        	
        	}
        	else
        		mState = State.IDLE;
        	
        	StateMachine ( );
        	updateHanDeviceTable ( );
    }
    
	private void createHanDeviceTable ( ) {
		if (mSerialDevice != null) {	
			for(int i = 0; i < mHanDeviceCnt; i ++) {
				TableRow row = new TableRow(this);
				TextView tv1 = new TextView(this);
				tv1.setText("#"+i);
				
				TextView tv2 = new TextView(this);
				tv2.setText("Smoke Sensor");
				
				TextView tvAlert = new TextView (this);
				tvAlert.setText(Integer.toString(mAlertCnt));
	
				TextView tvTamper = new TextView (this);
				tvTamper.setText(Integer.toString(mTamperCnt));
	
				row.addView(tv1);
				row.addView(tv2);
				row.addView(tvAlert);
				row.addView(tvTamper);
				
				mScrollViewHanDeviceTableLayout.addView(row);
			}
			mTextViewHanConnectedDeviceAns.setText(Integer.toString(mHanDeviceCnt));
		}
	}
	
	private void removeHanDeviceTable ( ) {
		int n = mScrollViewHanDeviceTableLayout.getChildCount();
		mScrollViewHanDeviceTableLayout.removeViewsInLayout(1, n-1);
	}
	
	private void updateHanDeviceTable ( ) {
		if (mScrollViewHanDeviceTableLayout.getChildCount() != (mHanDeviceCnt+1)) {
			removeHanDeviceTable ( );
			createHanDeviceTable ( );
		}
		
		if (mSerialDevice != null && mState == State.IDLE)
		{
			TableRow row = (TableRow) mScrollViewHanDeviceTableLayout.getChildAt(1);
			TextView tvAlert = (TextView)row.getChildAt(2);
			TextView tvTamper = (TextView)row.getChildAt(3);
			
			tvAlert.setText (Integer.toString(mAlertCnt));
			tvTamper.setText (Integer.toString(mTamperCnt));
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
	
	private void sendPacket (byte[] pkt) {
     	if (mSerialDevice != null) {
    	    try {
    	        mSerialDevice.write(pkt, 1000);
    	    } catch (IOException e) {
    	        e.printStackTrace();
    	    }
    	}
	}
	
	private void StateMachine () {
		Debug.d(TAG, "State = " + mState);
		
		if (mState == State.START) {
			sendPacket (RawData.CMBS_CMD_HELLO);
			mState = State.CMBS_CMD_HELLO;
		}
		else if (mState == State.CMBS_CMD_HELLO_RPLY) {
			sendPacket (RawData.CMBS_EV_DSR_SYS_START);
			mState = State.CMBS_EV_DSR_SYS_START;
		}
		else if (mState == State.CMBS_EV_DSR_SYS_START_RES) {
			sendPacket (RawData.CMBS_EV_DSR_RF_RESUME);
			sendPacket (RawData.CMBS_EV_DSR_HAN_MNGR_INIT);
			mState = State.CMBS_EV_DSR_HAN_MNGR_INIT;
		}
		else if (mState == State.CMBS_EV_DSR_HAN_MNGR_INIT_RES) {
			sendPacket (RawData.CMBS_EV_DSR_HAN_MNGR_START);
			mState = State.CMBS_EV_DSR_HAN_MNGR_START;
		}
		else if (mState == State.CMBS_EV_DSR_HAN_MNGR_START_RES) {
			sendPacket (RawData.CMBS_EV_DSR_PARAM_AREA_SET);
			mState = State.CMBS_EV_DSR_PARAM_AREA_SET;
		}
		else if (mState == State.CMBS_EV_DSR_PARAM_AREA_SET_RES) {
			sendPacket (RawData.CMBS_EV_DSR_HAN_MSG_RECV_REGISTER);
			sendPacket (RawData.CMBS_EV_DSR_HAN_DEVICE_READ_TABLE);
			mState = State.CMBS_EV_DSR_HAN_DEVICE_READ_TABLE;
		}	
	}
}

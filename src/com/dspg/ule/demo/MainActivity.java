package com.dspg.ule.demo;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.dspg.ule.driver.UsbSerialDriver;
import com.dspg.ule.driver.UsbSerialProber;
import com.dspg.ule.util.Debug;
import com.dspg.ule.util.HexDump;
import com.dspg.ule.util.SerialInputOutputManager;
import com.dspg.ule.cmbs.HanDevice;
import com.dspg.ule.cmbs.RawData;
import com.dspg.ule.cmbs.State;
import com.dspg.ule.cmbs.UnitType;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.UsbManager;
import android.telephony.SmsManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.LinearLayout;
//import android.widget.ScrollView;
import android.widget.Button;
import android.widget.TableRow;
import android.widget.TextView;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";
	
	private TextView mTextViewVersionName;
	private TextView mTextViewCmbsConnectedAns;
	private TextView mTextViewHanConnectedDeviceAns;
	private State mState;
	private LinkedList<HanDevice> mHanDeviceLinkedList;
	private SharedPreferences mPerf;
	
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
	    
	    // Multiple HAN Devices
	    mHanDeviceLinkedList = new LinkedList<HanDevice> ( );
	    createHanDeviceLinkedList ( );
	    
	    mPerf = PreferenceManager.getDefaultSharedPreferences(this);
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
            mTextViewHanConnectedDeviceAns.setText(R.string.not_available);
            mHanDeviceLinkedList.clear();
            updateHanDeviceTable ( );
        } else {
            try {
                mSerialDevice.open();
                mTextViewCmbsConnectedAns.setText(R.string.yes);
            } catch (IOException e) {
                Debug.e(TAG, "Error setting up device: " + e.getMessage());
                mTextViewCmbsConnectedAns.setText(R.string.no);
                mTextViewHanConnectedDeviceAns.setText(R.string.not_available);
                mHanDeviceLinkedList.clear();
                updateHanDeviceTable ( );
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
 
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.menu_about:
            return true;
        case R.id.menu_setting:
            Intent intent = new Intent(this, SettingActivity.class);
            startActivity(intent);
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    public void buttonAcOutletOnHandler (View target) {
    	sendPacket (RawData.CMBS_EV_DSR_HAN_MSG_RECV_AC_OUTLET_ON);
    }
	
    public void buttonAcOutletOffHandler (View target) {
    	sendPacket (RawData.CMBS_EV_DSR_HAN_MSG_RECV_AC_OUTLET_OFF);
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

    
    private void sendSMS (String m) {
        String phoneNumber = mPerf.getString ("setting_phone_number", "93794329");
        if (phoneNumber.matches(""))
        	phoneNumber = "93794329";
        Debug.d(TAG, phoneNumber);
        String message = "DSPG ULE Demo: Received " + m;
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, pi, null);        
    } 
    
    private void updateReceivedData(byte[] data) {
        final String message = "Read " + data.length + " bytes: " + HexDump.dumpHexString(data);
        	Debug.d(TAG, message);
//			mDumpTextView.append(message);
//        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
        	
        	if (Arrays.equals(data, RawData.CMBS_CMD_HELLO_RPLY))
        		mState = State.CMBS_CMD_HELLO_RPLY;
        	else if (Arrays.equals(data, RawData.CMBS_CMD_HELLO_RPLY2))
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
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MSG_RECV_SMOKE_TAMPER)) {
        		Debug.d (TAG, "Smoke TAMPER!!!");
        		HanDevice hd = mHanDeviceLinkedList.get(0);
        		hd.incTamperCnt();
        		mHanDeviceLinkedList.set(0, hd);
        		mState = State.IDLE;
        		boolean enableSms = mPerf.getBoolean ("enable_sms", false);
        		if (enableSms == true)
        			sendSMS("Smoke Tamper");
        	}
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MSG_RECV_SMOKE_ALERT)) {
        		Debug.d (TAG, "Smoke ALERT!!!");
        		HanDevice hd = mHanDeviceLinkedList.get(0);
        		hd.incAlertCnt();
        		mHanDeviceLinkedList.set(0, hd);
        		mState = State.IDLE;
        		boolean enableSms = mPerf.getBoolean ("enable_sms", false);
        		if (enableSms == true)
        			sendSMS("Smoke Alert");
        	}
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MSG_RECV_MOTION_TAMPER)) {
        		Debug.d (TAG, "Motion TAMPER!!!");
        		HanDevice hd = mHanDeviceLinkedList.get(1);
        		hd.incTamperCnt();
        		mHanDeviceLinkedList.set(1, hd);
        		mState = State.IDLE;
        		boolean enableSms = mPerf.getBoolean ("enable_sms", false);
        		if (enableSms == true)
        			sendSMS("Motion Tamper");
        	}
        	else if (Arrays.equals(data, RawData.CMBS_EV_DSR_HAN_MSG_RECV_AC_OUTLET_KEEP_ALIVE)) {
        		Debug.d (TAG, "AC Outlet KEEP ALIVE!!!");
        		//mAlertCnt ++;
        		HanDevice hd = mHanDeviceLinkedList.get(2);
        		hd.incKeepAliveCnt();
        		mHanDeviceLinkedList.set(2, hd);
        		mState = State.IDLE;
        		//boolean enableSms = mPerf.getBoolean ("enable_sms", false);
        		//if (enableSms == true)
        		//	sendSMS("Alert");
        	}
        	else
        		mState = State.IDLE;
        	
        	StateMachine ( );
        	updateHanDeviceTable ( );
    }
    
	private void removeHanDeviceTable ( ) {
		int n = mScrollViewHanDeviceTableLayout.getChildCount();
		
		for (int i = 1; i < n; i++) {
		    View child = mScrollViewHanDeviceTableLayout.getChildAt(i);
		    if (child instanceof TableRow) ((ViewGroup) child).removeAllViews();
		}
		mScrollViewHanDeviceTableLayout.removeViewsInLayout(1, n-1);
		
	}
	
    private void createHanDeviceLinkedList ( ) {
	    // TODO: hardcode mHanDeviceLinkedList content below
    	HanDevice hd1 = new HanDevice (1, UnitType.SMOKE_SENSOR);
    	HanDevice hd2 = new HanDevice (2, UnitType.MOTION_SENSOR);
    	HanDevice hd3 = new HanDevice (3, UnitType.AC_OUTLET);
    	
    	mHanDeviceLinkedList.clear();
    	mHanDeviceLinkedList.add(hd1);
    	mHanDeviceLinkedList.add(hd2);
    	mHanDeviceLinkedList.add(hd3);
    }

	
	private void updateHanDeviceTable ( ) {
		removeHanDeviceTable ( );
		if (mSerialDevice != null && mHanDeviceLinkedList.size() > 0) {
			for (int i = 0; i < mHanDeviceLinkedList.size(); i ++) {
				HanDevice hd = mHanDeviceLinkedList.get(i);
				
				TableRow row = new TableRow(this);
				TextView tv1 = new TextView(this);
				tv1.setText("#" + hd.getDeviceId());
				
				TextView tv2 = new TextView(this);				
				if (hd.getUnitType() == UnitType.SMOKE_SENSOR) {
					tv2.setText("Smoke (0x" + Integer.toHexString(UnitType.SMOKE_SENSOR.getUnitType()) + ")");
				}
				else if (hd.getUnitType() == UnitType.MOTION_SENSOR) {
					tv2.setText("Motion (0x" + Integer.toHexString(UnitType.MOTION_SENSOR.getUnitType()) + ")");
				}		
				else if (hd.getUnitType() == UnitType.AC_OUTLET) {
					tv2.setText("AC Outlet (0x" + Integer.toHexString(UnitType.AC_OUTLET.getUnitType()) + ")");
				}	

				TextView tvKeepAlive = new TextView (this);
				tvKeepAlive.setText(Integer.toString(hd.getKeepAliveCnt()));
				
				TextView tvAlert = new TextView (this);
				tvAlert.setText(Integer.toString(hd.getAlertCnt()));
	
				TextView tvTamper = new TextView (this);
				tvTamper.setText(Integer.toString(hd.getTamperCnt()));
	
				row.addView(tv1);
				row.addView(tv2);
				row.addView(tvKeepAlive);
				row.addView(tvAlert);
				row.addView(tvTamper);
				
				mScrollViewHanDeviceTableLayout.addView(row);				
			}
			mTextViewHanConnectedDeviceAns.setText(Integer.toString(mHanDeviceLinkedList.size()));
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

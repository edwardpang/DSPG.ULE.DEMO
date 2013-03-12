package com.dspg.ule.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.dspg.ule.driver.UsbId;
import com.dspg.ule.util.Debug;
import com.dspg.ule.util.HexDump;

public class DspgCmbsUsbSerialDriver implements UsbSerialDriver {

	private static final String TAG = "DspgCmbsUsbSerialDriver";
	
    public static final int DEFAULT_READ_BUFFER_SIZE = 16 * 1024;
    public static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;

    protected final UsbDevice mDevice;
    protected final UsbDeviceConnection mConnection;

    protected final Object mReadBufferLock = new Object();
    protected final Object mWriteBufferLock = new Object();

    /** Internal read buffer.  Guarded by {@link #mReadBufferLock}. */
    protected byte[] mReadBuffer;

    /** Internal write buffer.  Guarded by {@link #mWriteBufferLock}. */
    protected byte[] mWriteBuffer;

    private UsbInterface mControlInterface;
    private UsbInterface mDataInterface;

    private UsbEndpoint mControlEndpoint;
    private UsbEndpoint mReadEndpoint;
    private UsbEndpoint mWriteEndpoint;

    private boolean mRts = false;
    private boolean mDtr = false;
    private int mBaudRate;
    private int mDataBits;
    private int mStopBits;
    private int mParity;

//    private static final int USB_RECIP_INTERFACE = 0x01;
//    private static final int USB_RT_ACM = UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE;

//    public static final int USB_TYPE_STANDARD = 0x00 << 5;
//    public static final int USB_TYPE_CLASS = 0x00 << 5;
//    public static final int USB_TYPE_VENDOR = 0x00 << 5;
//    public static final int USB_TYPE_RESERVED = 0x00 << 5;

    public static final int USB_RECIP_DEVICE = 0x00;
    public static final int USB_RECIP_INTERFACE = 0x01;
    public static final int USB_RECIP_ENDPOINT = 0x02;
    public static final int USB_RECIP_OTHER = 0x03;
    
    public static final int USB_INTERFACE_OUT_REQTYPE =
            UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE | UsbConstants.USB_DIR_OUT;

    public static final int USB_INTERFACE_IN_REQTYPE =
            UsbConstants.USB_TYPE_CLASS | USB_RECIP_INTERFACE | UsbConstants.USB_DIR_IN;

    private static final int SET_LINE_CODING = 0x20;  // USB CDC 1.1 section 6.2
    private static final int GET_LINE_CODING = 0x21;
    private static final int SET_CONTROL_LINE_STATE = 0x22;  // USB CDC 1.1 section 6.2
    private static final int SEND_BREAK = 0x23;  // USB CDC 1.1 section 6.2
    
    public DspgCmbsUsbSerialDriver(UsbDevice device, UsbDeviceConnection connection) {
        mDevice = device;
        mConnection = connection;

        mReadBuffer = new byte[DEFAULT_READ_BUFFER_SIZE];
        mWriteBuffer = new byte[DEFAULT_WRITE_BUFFER_SIZE];
    }

    /**
     * Returns the currently-bound USB device.
     *
     * @return the device
     */
    public final UsbDevice getDevice() {
        return mDevice;
    }

    /**
     * Sets the size of the internal buffer used to exchange data with the USB
     * stack for read operations.  Most users should not need to change this.
     *
     * @param bufferSize the size in bytes
     */
    public final void setReadBufferSize(int bufferSize) {
        synchronized (mReadBufferLock) {
            if (bufferSize == mReadBuffer.length) {
                return;
            }
            mReadBuffer = new byte[bufferSize];
        }
    }

    /**
     * Sets the size of the internal buffer used to exchange data with the USB
     * stack for write operations.  Most users should not need to change this.
     *
     * @param bufferSize the size in bytes
     */
    public final void setWriteBufferSize(int bufferSize) {
        synchronized (mWriteBufferLock) {
            if (bufferSize == mWriteBuffer.length) {
                return;
            }
            mWriteBuffer = new byte[bufferSize];
        }
    }

    @Override
    public void open() throws IOException {
        Debug.d(TAG, "claiming interfaces, count=" + mDevice.getInterfaceCount());

        Debug.d(TAG, "Claiming control interface.");
        mControlInterface = mDevice.getInterface(0); // TODO hardcode interface number here
        Debug.d(TAG, "Control iface=" + mControlInterface);
        // class should be USB_CLASS_COMM

        if (!mConnection.claimInterface(mControlInterface, true)) {
            throw new IOException("Could not claim control interface.");
        }
        mControlEndpoint = mControlInterface.getEndpoint(0);
        Debug.d(TAG, "Control endpoint direction: " + mControlEndpoint.getDirection());

        Debug.d(TAG, "Claiming data interface.");
        mDataInterface = mDevice.getInterface(1); // TODO hardcode interface number here
        Debug.d(TAG, "data iface=" + mDataInterface);
        // class should be USB_CLASS_CDC_DATA

        if (!mConnection.claimInterface(mDataInterface, true)) {
            throw new IOException("Could not claim data interface.");
        }
        mWriteEndpoint = mDataInterface.getEndpoint(0);
        Debug.d(TAG, "Write endpoint direction: " + mWriteEndpoint.getDirection());
        mReadEndpoint = mDataInterface.getEndpoint(1);
        Debug.d(TAG, "Read endpoint direction: " + mReadEndpoint.getDirection());

        sendBreak ( );
        Debug.d( TAG, "Current Baudrate: " + getBaudrate ( ));
        Debug.d( TAG, "Current DataBits: " + getDataBits ( ));
        Debug.d( TAG, "Current Parity: " + getParity ( ));
        Debug.d( TAG, "Current StopBits: " + getStopBits ( ));
        
        Debug.d(TAG, "Setting Control Line State");
        mDtr = true;
        mRts = true;
        setDtrRts ();

        Debug.d(TAG, "Setting line coding to 115200/8N1");
        mBaudRate = 115200;
        mDataBits = DATABITS_8;
        mParity = PARITY_NONE;
        mStopBits = STOPBITS_1;
        setParameters(mBaudRate, mDataBits, mStopBits, mParity);        
    }

    private int sendAcmControlMessage(int request, int value, byte[] buf) {
    	if (buf != null)
    		Debug.d(TAG, HexDump.dumpHexString(buf));
        return mConnection.controlTransfer(
        		USB_INTERFACE_OUT_REQTYPE, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
    }

    private int recvAcmControlMessage(int request, int value, byte[] buf) {
    	//byte[] retval = new byte[10];
        return mConnection.controlTransfer(USB_INTERFACE_IN_REQTYPE, request, value, 0, buf, buf != null ? buf.length : 0, 5000);
    }
    
    @Override
    public void close() throws IOException {
        mConnection.close();
    }

    @Override
    public int read(byte[] dest, int timeoutMillis) throws IOException {
        final int numBytesRead;
        synchronized (mReadBufferLock) {
            int readAmt = Math.min(dest.length, mReadBuffer.length);
            numBytesRead = mConnection.bulkTransfer(mReadEndpoint, mReadBuffer, readAmt,
                    timeoutMillis);
            if (numBytesRead < 0) {
                // This sucks: we get -1 on timeout, not 0 as preferred.
                // We *should* use UsbRequest, except it has a bug/api oversight
                // where there is no way to determine the number of bytes read
                // in response :\ -- http://b.android.com/28023
                return 0;
            }
            System.arraycopy(mReadBuffer, 0, dest, 0, numBytesRead);
            Debug.d(TAG, HexDump.dumpHexString(dest));
        }
        return numBytesRead;
    }

    @Override
    public int write(byte[] src, int timeoutMillis) throws IOException {
        // TODO(mikey): Nearly identical to FtdiSerial write. Refactor.
        int offset = 0;

        while (offset < src.length) {
            final int writeLength;
            final int amtWritten;

            synchronized (mWriteBufferLock) {
                final byte[] writeBuffer;

                writeLength = Math.min(src.length - offset, mWriteBuffer.length);
                if (offset == 0) {
                    writeBuffer = src;
                } else {
                    // bulkTransfer does not support offsets, make a copy.
                    System.arraycopy(src, offset, mWriteBuffer, 0, writeLength);
                    writeBuffer = mWriteBuffer;
                }

                amtWritten = mConnection.bulkTransfer(mWriteEndpoint, writeBuffer, writeLength,
                        timeoutMillis);
            }
            if (amtWritten <= 0) {
                throw new IOException("Error writing " + writeLength
                        + " bytes at offset " + offset + " length=" + src.length);
            }

            Debug.d(TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            Debug.d(TAG, HexDump.dumpHexString(src));
            offset += amtWritten;
        }
        return offset;
    }
    @Override
    public void setParameters(int baudRate, int dataBits, int stopBits, int parity) throws IOException {
        byte stopBitsByte;
        switch (stopBits) {
            case STOPBITS_1: stopBitsByte = 0; break;
            case STOPBITS_1_5: stopBitsByte = 1; break;
            case STOPBITS_2: stopBitsByte = 2; break;
            default: throw new IllegalArgumentException("Bad value for stopBits: " + stopBits);
        }

        byte parityBitesByte;
        switch (parity) {
            case PARITY_NONE: parityBitesByte = 0; break;
            case PARITY_ODD: parityBitesByte = 1; break;
            case PARITY_EVEN: parityBitesByte = 2; break;
            case PARITY_MARK: parityBitesByte = 3; break;
            case PARITY_SPACE: parityBitesByte = 4; break;
            default: throw new IllegalArgumentException("Bad value for parity: " + parity);
        }

        byte[] msg = {
                (byte) ( baudRate & 0xff),
                (byte) ((baudRate >> 8 ) & 0xff),
                (byte) ((baudRate >> 16) & 0xff),
                (byte) ((baudRate >> 24) & 0xff),
                stopBitsByte,
                parityBitesByte,
                (byte) dataBits};
        int retval = sendAcmControlMessage(SET_LINE_CODING, 0, msg);
        Debug.d(TAG, "SET_LINE_CODING: " + HexDump.dumpHexString(msg) + "(" + retval + " bytes sent)");

        msg[0] = 0;
        msg[1] = 0;
        msg[2] = 0;
        msg[3] = 0;
        msg[4] = 0;
        msg[5] = 0;
        msg[6] = 0;
        retval = recvAcmControlMessage (GET_LINE_CODING, 0, msg);
        Debug.d (TAG, "GET_LINE_CODING: " + HexDump.dumpHexString(msg) + "(" + retval + " bytes received)");
    }

    public int getBaudrate () {
    	byte[] msg = new byte[7];
        int retval = recvAcmControlMessage (GET_LINE_CODING, 0, msg);
        Debug.d (TAG, "GET_LINE_CODING: " + HexDump.dumpHexString(msg) + "(" + retval + " bytes received)");
        return ((int) (msg[0] & 0xFF) + 
        		(int) ((msg[1] << 8) & 0xFF00) +
        		(int) ((msg[2] << 16) & 0xFF0000)+
        		(int) ((msg[3] << 24) & 0xFF000000));
    }
 
    public int getDataBits () {
    	byte[] msg = new byte[7];
        int retval = recvAcmControlMessage (GET_LINE_CODING, 0, msg);
        Debug.d (TAG, "GET_LINE_CODING: " + HexDump.dumpHexString(msg) + "(" + retval + " bytes received)");
        return ((int) msg[4]);
    	
    }
    
    public int getParity () {
    	byte[] msg = new byte[7];
        int retval = recvAcmControlMessage (GET_LINE_CODING, 0, msg);
        Debug.d (TAG, "GET_LINE_CODING: " + HexDump.dumpHexString(msg) + "(" + retval + " bytes received)");
        return ((int) msg[5]);
    	
    }
    
    public int getStopBits () {
    	byte[] msg = new byte[7];
        int retval = recvAcmControlMessage (GET_LINE_CODING, 0, msg);
        Debug.d (TAG, "GET_LINE_CODING: " + HexDump.dumpHexString(msg) + "(" + retval + " bytes received)");
        return ((int) msg[6]);
    	
    }
    
    public void sendBreak ( ) {
        int retval = sendAcmControlMessage (SEND_BREAK, 0, null);
        Debug.d (TAG, "SEND_BREAK: (" + retval + " bytes sent)");    	
    }
    
    @Override
    public boolean getCD() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getCTS() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getDSR() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getDTR() throws IOException {
        return mDtr;  // TODO
    }

    @Override
    public void setDTR(boolean value) throws IOException {
        mDtr = value;
        setDtrRts();
    }

    @Override
    public boolean getRI() throws IOException {
        return false;  // TODO
    }

    @Override
    public boolean getRTS() throws IOException {
        return mRts;  // TODO
    }

    @Override
    public void setRTS(boolean value) throws IOException {
        mRts = value;
        setDtrRts();
    };

    private void setDtrRts() {
        int value = (mRts ? 0x2 : 0) | (mDtr ? 0x1 : 0);
        int retval = sendAcmControlMessage(SET_CONTROL_LINE_STATE, value, null);
        Debug.d(TAG, "setDtrRts sent " + retval + " bytes");
    }
    
    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(Integer.valueOf(UsbId.VID_DSPG),
                new int[] {
                    UsbId.PID_DSPG_CMBS,
                });
        return supportedDevices;
    }
}

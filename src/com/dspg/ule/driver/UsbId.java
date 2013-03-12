package com.dspg.ule.driver;

/**
 * Registry of USB vendor/product ID constants.
 *
 * Culled from various sources; see
 * <a href="http://www.linux-usb.org/usb.ids">usb.ids</a> for one listing.
 */
public final class UsbId {

	// DSPG
    public static final int VID_DSPG = 0x3006;
    public static final int PID_DSPG_CMBS = 0x1977;

    private UsbId() {
        throw new IllegalAccessError("Non-instantiable class.");
    }

}

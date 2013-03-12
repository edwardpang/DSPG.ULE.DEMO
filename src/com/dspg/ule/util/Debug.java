package com.dspg.ule.util;

import android.util.Log;

public class Debug {
	public static void v(Object t, Object s) {
		Log.v(t.toString(), "===> " + s.toString());
	}

	public static void i(Object t, Object s) {
		Log.i(t.toString(), "===> " + s.toString());
	}
	
	public static void d(Object t, Object s) {
		Log.d(t.toString(), "===> " + s.toString());
	}

	public static void e(Object t, Object s) {
		Log.e(t.toString(), "===> " + s.toString());
	}
}

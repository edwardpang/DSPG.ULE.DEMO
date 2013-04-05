package com.dspg.ule.cmbs;

import com.dspg.ule.cmbs.UnitType;

public class HanDevice {
	private final int 		deviceId;
	private final UnitType	unitType;
	private int keepAliveCnt;
	private int alertCnt;
	private int tamperCnt;
	
	public HanDevice (int id, UnitType t) {
		this.deviceId = id;
		this.unitType = t;
		this.keepAliveCnt = 0;
		this.alertCnt = 0;
		this.tamperCnt = 0;
	}
	
	public void resetCnt ( ) {
		this.keepAliveCnt = 0;
		this.alertCnt = 0;
		this.tamperCnt = 0;
	}
	
	public int getDeviceId ( ) {
		return this.deviceId;
	}
	
	public UnitType getUnitType ( ) {
		return this.unitType;
	}
	
	public int getKeepAliveCnt ( ) {
		return this.keepAliveCnt;
	}

	public int getAlertCnt ( ) {
		return this.alertCnt;
	}

	public int getTamperCnt ( ) {
		return this.tamperCnt;
	}

	public void incKeepAliveCnt ( ) {
		this.keepAliveCnt ++;
	}

	public void incAlertCnt ( ) {
		this.alertCnt ++;
	}
	
	public void incTamperCnt ( ) {
		this.tamperCnt ++;
	}
}

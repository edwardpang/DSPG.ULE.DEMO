package com.dspg.ule.cmbs;

public enum UnitType {
	SMOKE_SENSOR (0x204),
	MOTION_SENSOR (0x203),
	AC_OUTLET (0x106);
	
	private final int n;
	
	private UnitType (int n) {
		this.n = n;
	}
	
	public int getUnitType ( ) {
		return (this.n);
	}
	
	public String getUnitTypeName (UnitType n) {
		String retval = new String ( );
		if (n == UnitType.SMOKE_SENSOR) {
			retval = "Smoke (0x" + Integer.toHexString(n.getUnitType()) + ")";
		}
		else if (n == UnitType.MOTION_SENSOR) {
			retval = "Motion (0x" + Integer.toHexString(n.getUnitType()) + ")";
		}		
		else if (n == UnitType.AC_OUTLET) {
			retval = "AC Outlet (0x" + Integer.toHexString(n.getUnitType()) + ")";
		}		
		return retval;
	}
}

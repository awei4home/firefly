package com.firefly.codec.spdy.decode.utils;

abstract public class NumberProcessUtils {
	
	public static int toUnsignedInteger(byte i) {
		return  i & 0xff;
	}
	
	public static int toUnsignedInteger(short i) {
		return i & 0xff_ff;
	}
	
	public static long toUnsignedLong(int i) {
		return i & 0xff_ff_ff_ffL;
	}
	
	public static int to24bitsInteger(byte highOrder, short lowOrder) {
		int x = toUnsignedInteger(highOrder);
		x <<= 16;
		x += toUnsignedInteger(lowOrder);
		return x;
	}
	
	public static short to15bitsShort(short i) {
		return (short)(i & 0x7F_FF);
	}
	
	public static int to31bitsInteger(int i) {
		return i &  0x7F_FF_FF_FF;
	}
}

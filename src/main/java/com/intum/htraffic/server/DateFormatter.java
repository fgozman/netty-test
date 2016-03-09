package com.intum.htraffic.server;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public abstract class DateFormatter {
	private static final TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");
	private static ThreadLocal<SimpleDateFormat> tl = new ThreadLocal<SimpleDateFormat>(){
		protected SimpleDateFormat initialValue() {
			//SimpleDateFormat dateFormat =  new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
			SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
			dateFormat.setTimeZone(TIME_ZONE);	
			return dateFormat;
		};
	};
	public static final DateFormat createUTCDateFormat(){
		return tl.get();
	}
	
	public static void clear(){
		tl.remove();
	}
}


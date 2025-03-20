package com.example.demo.common.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {

    private DateUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static Long getUnixTimestamp() {
        return System.currentTimeMillis() / 1000;
    }

    //날짜 포맷으로 현 날짜 구하기
    public static String getCurrentDate(){
        SimpleDateFormat format1 = new SimpleDateFormat ( "yyyy_MM_dd__HH_mm_ss");
        Date date = new Date();
        return format1.format(date);
    }
}

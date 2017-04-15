package com.xunce.qrcoder;

/**
 * Created by yangxu on 2017/4/6.
 */

public class StringDecodeUtil {

    public static String getIMEIFromSim808(String string){
        String IMEI = "";
        String[] strArray = string.split(";");
        for (String str: strArray) {
            if(str.contains("IMEI")){
                IMEI = str.split(":")[1];
                return IMEI;
            }
        }
        return IMEI;
    }

}

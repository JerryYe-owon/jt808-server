package org.yzh.commons.util;

public class SimUtils
{

    /**
     * 根据安装后终端自身的手机号转换。
     * 手机号不足12位，则在前补充数字，
     * 大陆手机号补充数字0，
     * 港澳台则根据其区号进行位数补充。
     */
    public static String buildMobileNo12(String mobileNo)
    {
        if (mobileNo == null || mobileNo.isEmpty())
        {
            return null;
        }
        if (mobileNo.length() == 12)
        {
            return mobileNo;
        }
        if (mobileNo.length() == 11)
        {
            return "0" + mobileNo; // +86
        }
        // todo
        return String.format("%012d", Long.parseLong(mobileNo));
    }
}

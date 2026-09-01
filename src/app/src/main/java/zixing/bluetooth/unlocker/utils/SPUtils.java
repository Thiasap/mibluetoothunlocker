package zixing.bluetooth.unlocker.utils;

import android.content.Context;
import android.content.SharedPreferences;

import zixing.bluetooth.unlocker.UnlockerApp;

public class SPUtils {

    public static SPUtils xsp;
    public SharedPreferences sp;

    private SPUtils() {
    }

    public static synchronized SPUtils getInstance() {
        if (xsp == null) {
            xsp = new SPUtils();
        }
        return xsp;
    }

    public static boolean isEnableModule = false;

    public void init(Context context) {
        if (sp == null) {
            sp = context.getSharedPreferences("config", Context.MODE_PRIVATE);
        }
        isEnableModule = UnlockerApp.isModuleEnabled();
    }

    public static String getString(String key, String def) {
        SharedPreferences prefs = SPUtils.getInstance().sp;
        return prefs == null ? def : prefs.getString(key, def);
    }

    public static int getInt(String key, int def) {
        return SPUtils.getInstance().sp.getInt(key, def);
    }

    public static float getFloat(String key, float def) {
        return SPUtils.getInstance().sp.getFloat(key, def);
    }

    public static long getLong(String key, long def) {
        return SPUtils.getInstance().sp.getLong(key, def);
    }

    public static boolean getBoolean(String key, boolean def) {
        return SPUtils.getInstance().sp.getBoolean(key, def);
    }

    public static boolean setString(String key, String v) {
        SharedPreferences prefs = SPUtils.getInstance().sp;
        return prefs != null && prefs.edit().putString(key, v).commit();
    }

    public static boolean setInt(String key, int v) {
        return SPUtils.getInstance().sp.edit().putInt(key, v).commit();
    }

    public static boolean setBoolean(String key, boolean v) {
        return SPUtils.getInstance().sp.edit().putBoolean(key, v).commit();
    }

    public static boolean setFloat(String key, float v) {
        return SPUtils.getInstance().sp.edit().putFloat(key, v).commit();
    }

    public static boolean setLong(String key, long v) {
        return SPUtils.getInstance().sp.edit().putLong(key, v).commit();
    }
}

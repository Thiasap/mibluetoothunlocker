package zixing.bluetooth.unlocker;

import android.app.Application;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import zixing.bluetooth.unlocker.activity.MainActivity;
import zixing.bluetooth.unlocker.utils.ConfigUtil;
import zixing.bluetooth.unlocker.utils.SPUtils;

public class UnlockerApp extends Application implements XposedServiceHelper.OnServiceListener {

    private static volatile XposedService sService;

    @Override
    public void onCreate() {
        super.onCreate();
        SPUtils.getInstance().init(this);
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        sService = service;
        SPUtils.isEnableModule = true;
        SPUtils.getInstance().init(this);
        ConfigUtil.initXSP(this);
        ConfigUtil.syncWithRemote(service);
        if (MainActivity.self != null) {
            MainActivity.self.runOnUiThread(() -> {
                if (MainActivity.self != null) {
                    MainActivity.self.readConfig();
                }
            });
        }
    }

    @Override
    public void onServiceDied(XposedService service) {
        if (sService == service) {
            sService = null;
            SPUtils.isEnableModule = false;
        }
    }

    public static boolean isModuleEnabled() {
        return sService != null;
    }

    public static XposedService getService() {
        return sService;
    }
}

package zixing.bluetooth.unlocker.Xp;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import zixing.bluetooth.unlocker.utils.BluetoothHelper;
import zixing.bluetooth.unlocker.utils.ConfigUtil;
import zixing.bluetooth.unlocker.utils.ReflectUtil;

public class MyXp extends XposedModule {

    private static final String TAG = "hookhelper";
    public static volatile MyXp instance;
    private String processName;

    public MyXp() {
    }

    public static void myLog(String msg) {
        Log.i(TAG, msg);
        MyXp module = instance;
        if (module != null) {
            module.log(Log.INFO, TAG, msg);
        }
    }

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        instance = this;
        processName = param.getProcessName();
        bindRemoteConfig();
        myLog("onModuleLoaded process=" + processName
                + " framework=" + getFrameworkName()
                + " api=" + getApiVersion());
    }

    private void bindRemoteConfig() {
        ConfigUtil.remoteReader = (key, def) -> getRemotePreferences(ConfigUtil.REMOTE_GROUP).getString(key, def);
    }

    @Override
    public void onPackageReady(@NonNull XposedModuleInterface.PackageReadyParam param) {
        String packageName = param.getPackageName();
        ClassLoader classLoader = param.getClassLoader();
        if ("com.android.settings".equals(packageName)) {
            hookSettings(classLoader);
        } else if ("com.android.systemui".equals(packageName)) {
            hookSystemUi(classLoader);
        }
    }

    @Override
    public boolean onHotReloading(@NonNull XposedModuleInterface.HotReloadingParam param) {
        // 本模块依赖已构造的 SystemUI/Settings 实例，热更新后无法可靠找回这些对象。
        BluetoothHelper.releaseGatt();
        myLog("skip hot reload, reboot required. process=" + processName);
        return false;
    }

    @Override
    public void onHotReloaded(@NonNull XposedModuleInterface.HotReloadedParam param) {
        instance = this;
        processName = param.getProcessName();
        bindRemoteConfig();
        for (XposedInterface.HookHandle handle : param.getOldHookHandles()) {
            handle.unhook();
        }
        myLog("onHotReloaded process=" + processName);
        Context app = findCurrentApplication();
        ClassLoader classLoader = app != null ? app.getClassLoader() : null;
        if (classLoader == null) {
            myLog("onHotReloaded missing app classloader");
            return;
        }
        if ("com.android.settings".equals(processName)) {
            hookSettings(classLoader);
        } else if ("com.android.systemui".equals(processName)) {
            hookSystemUi(classLoader);
        }
        if (app != null) {
            applyBluetoothUnlockConfig(app);
        }
    }

    private Context findCurrentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object application = activityThread.getMethod("currentApplication").invoke(null);
            if (application instanceof Context) {
                return (Context) application;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void applyBluetoothUnlockConfig(Context context1) {
        try {
            String mac = ConfigUtil.getString("mac", "", 2);
            if (mac == null || mac.isEmpty()) {
                return;
            }
            Class<?> utilClass = ReflectUtil.findClass("android.security.MiuiLockPatternUtils", context1.getClassLoader());
            Object utilclass = ReflectUtil.newInstance(utilClass, context1);
            if (ConfigUtil.BASE_MODE.equals(mac)) {
                ReflectUtil.callMethod(utilclass, "setBluetoothUnlockEnabled", false);
                ReflectUtil.callMethod(utilclass, "setBluetoothAddressToUnlock", "");
                ReflectUtil.callMethod(utilclass, "setBluetoothNameToUnlock", "");
                ReflectUtil.callMethod(utilclass, "setBluetoothKeyToUnlock", "");
            } else {
                ReflectUtil.callMethod(utilclass, "setBluetoothUnlockEnabled", true);
                ReflectUtil.callMethod(utilclass, "setBluetoothAddressToUnlock", mac);
                ReflectUtil.callMethod(utilclass, "setBluetoothNameToUnlock", "mibluetoothunlocker");
                ReflectUtil.callMethod(utilclass, "setBluetoothKeyToUnlock", "mibluetoothunlocker");
            }
        } catch (Throwable ex) {
            myLog("applyBluetoothUnlockConfig error: " + ex);
        }
    }

    private void hookMethod(Executable executable, XposedInterface.Hooker hooker) {
        hook(executable).intercept(hooker);
    }

    private void hookAllMethods(Class<?> clazz, String name, XposedInterface.Hooker hooker) {
        if (clazz == null) {
            return;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (name.equals(method.getName())) {
                hookMethod(method, hooker);
            }
        }
    }

    private void hookAllConstructors(Class<?> clazz, XposedInterface.Hooker hooker) {
        if (clazz == null) {
            return;
        }
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            hookMethod(constructor, hooker);
        }
    }

    private void hookSettings(ClassLoader classLoader) {
        try {
        final Class<?> MiuiSecurityBluetoothMatchDeviceFragmentClass = ReflectUtil.findClass(
                "com.android.settings.MiuiSecurityBluetoothMatchDeviceFragment", classLoader);
        final Class<?> MiuiLockPatternUtilClass = ReflectUtil.findClass(
                "android.security.MiuiLockPatternUtils", classLoader);

        final String[] macrep = new String[1];
        hookMethod(ReflectUtil.findMethod(Application.class, "attach", Context.class), chain -> {
            Object result = chain.proceed();
            Context context1 = (Context) chain.getArg(0);
            context = context1;
            macrep[0] = ConfigUtil.getString("mac", "", 1);
            if (macrep[0] != null && !macrep[0].isEmpty()) {
                if (ConfigUtil.BASE_MODE.equals(macrep[0])) {
                    Object utilclass = ReflectUtil.newInstance(MiuiLockPatternUtilClass, context1);
                    ReflectUtil.callMethod(utilclass, "setBluetoothUnlockEnabled", false);
                    ReflectUtil.callMethod(utilclass, "setBluetoothAddressToUnlock", "");
                    ReflectUtil.callMethod(utilclass, "setBluetoothNameToUnlock", "");
                    ReflectUtil.callMethod(utilclass, "setBluetoothKeyToUnlock", "");
                    macrep[0] = "";
                } else {
                    Object utilclass = ReflectUtil.newInstance(MiuiLockPatternUtilClass, context1);
                    ReflectUtil.callMethod(utilclass, "setBluetoothUnlockEnabled", true);
                    ReflectUtil.callMethod(utilclass, "setBluetoothAddressToUnlock", macrep[0]);
                    ReflectUtil.callMethod(utilclass, "setBluetoothNameToUnlock", "mibluetoothunlocker");
                    ReflectUtil.callMethod(utilclass, "setBluetoothKeyToUnlock", "mibluetoothunlocker");
                }
            }
            return result;
        });

        hookMethod(ReflectUtil.findMethod(MiuiLockPatternUtilClass, "getBluetoothAddressToUnlock"), chain -> {
            Object result = chain.proceed();
            if (macrep[0] != null && !macrep[0].isEmpty()) {
                if (ConfigUtil.BASE_MODE.equals(macrep[0])) {
                    return null;
                }
                return macrep[0];
            }
            return result;
        });

        myLog("--------------" + MiuiLockPatternUtilClass + "------------");

        hookMethod(ReflectUtil.findMethod(MiuiSecurityBluetoothMatchDeviceFragmentClass, "switchToTapConfirmingLayout"), chain -> {
            try {
                myLog("--------------开始hook switchToTapConfirmingLayout MiuiLockPatternUtilClass------------");

                Field mLockPatternUtilsField = MiuiSecurityBluetoothMatchDeviceFragmentClass.getDeclaredField("mLockPatternUtils");
                mLockPatternUtilsField.setAccessible(true);
                mLockPatternUtils = mLockPatternUtilsField.get(chain.getThisObject());

                Field mDeviceField = MiuiSecurityBluetoothMatchDeviceFragmentClass.getDeclaredField("mDevice");
                mDeviceField.setAccessible(true);
                Object mDevice = mDeviceField.get(chain.getThisObject());

                Field mDeviceTypeField = MiuiSecurityBluetoothMatchDeviceFragmentClass.getDeclaredField("mDeviceType");
                mDeviceTypeField.setAccessible(true);
                Object mDeviceType = mDeviceTypeField.get(chain.getThisObject());

                Field mDeviceMajorClassField = MiuiSecurityBluetoothMatchDeviceFragmentClass.getDeclaredField("mDeviceMajorClass");
                mDeviceMajorClassField.setAccessible(true);
                Object mDeviceMajorClass = mDeviceMajorClassField.get(chain.getThisObject());

                Field mDeviceMinorClassField = MiuiSecurityBluetoothMatchDeviceFragmentClass.getDeclaredField("mDeviceMinorClass");
                mDeviceMinorClassField.setAccessible(true);
                Object mDeviceMinorClass = mDeviceMinorClassField.get(chain.getThisObject());

                ReflectUtil.callMethod(mLockPatternUtils, "setBluetoothUnlockEnabled", true);
                ReflectUtil.callMethod(mLockPatternUtils, "setBluetoothAddressToUnlock",
                        ReflectUtil.callMethod(mDevice, "getAddress").toString());
                ReflectUtil.callMethod(mLockPatternUtils, "setBluetoothNameToUnlock",
                        ReflectUtil.callMethod(mDevice, "getName").toString());
                ReflectUtil.callMethod(chain.getThisObject(), "saveDevice",
                        ReflectUtil.callMethod(chain.getThisObject(), "getContext"),
                        ReflectUtil.callMethod(mDevice, "getAddress").toString(),
                        mDeviceType, mDeviceMajorClass, mDeviceMinorClass, true);
                ReflectUtil.callMethod(chain.getThisObject(), "switchToSucceedLayout");
                myLog("--------------结束hook switchToTapConfirmingLayout------------");
            } catch (Exception ex) {
                myLog("-------------- 发生错误 ： " + ex);
            }
            return null;
        });

        Class<?> MiuiSecurityBluetoothDeviceInfoFragment = ReflectUtil.findClassIfExists(
                "com.android.settings.MiuiSecurityBluetoothDeviceInfoFragment",
                classLoader
        );
        if (MiuiSecurityBluetoothDeviceInfoFragment == null) {
            return;
        }
        Method onCreate;
        try {
            onCreate = ReflectUtil.findMethod(MiuiSecurityBluetoothDeviceInfoFragment, "onCreate", android.os.Bundle.class);
        } catch (NoSuchMethodError e) {
            onCreate = null;
        }
        XposedInterface.Hooker onCreateHooker = chain -> {
            myLog("-------------- before hook MiuiSecurityBluetoothDeviceInfoFragment.onCreate ------------");
            Object result = chain.proceed();
            myLog("-------------- after hook MiuiSecurityBluetoothDeviceInfoFragment.onCreate ------------");
            try {
                Field mUnlockListenerField = MiuiSecurityBluetoothDeviceInfoFragment.getDeclaredField("mUnlockListener");
                mUnlockListenerField.setAccessible(true);
                mUnlockListener = mUnlockListenerField.get(chain.getThisObject());

                Field mLockPatternUtilsField = MiuiSecurityBluetoothDeviceInfoFragment.getDeclaredField("mLockPatternUtils");
                mLockPatternUtilsField.setAccessible(true);
                mLockPatternUtils = mLockPatternUtilsField.get(chain.getThisObject());

                Class<?> clazzActivity = mUnlockListener.getClass();
                hookAllMethods(clazzActivity, "onUnlocked", unlockedChain -> {
                    myLog("-------------- before hook com.android.settings.MiuiSecurityBluetoothDeviceInfoFragment$1 ------------");
                    Object[] args = unlockedChain.getArgs().toArray();
                    if (args.length == 1 && "0".equals(String.valueOf(args[0]))) {
                        args[0] = (byte) 1;
                        BluetoothHelper.CanUnlockByBluetoothOldDirect(context,
                                ReflectUtil.callMethod(mLockPatternUtils, "getBluetoothAddressToUnlock").toString(),
                                classLoader, 1);
                        myLog("-------------- after hook com.android.settings.MiuiSecurityBluetoothDeviceInfoFragment$1 ------------");
                        return unlockedChain.proceed(args);
                    }
                    Object unlockedResult = unlockedChain.proceed();
                    myLog("-------------- after hook com.android.settings.MiuiSecurityBluetoothDeviceInfoFragment$1 ------------");
                    return unlockedResult;
                });
            } catch (Exception ex) {
                myLog("-------------- onCreate hook error ： " + ex);
            }
            return result;
        };
        if (onCreate != null) {
            hookMethod(onCreate, onCreateHooker);
        } else {
            hookAllMethods(MiuiSecurityBluetoothDeviceInfoFragment, "onCreate", onCreateHooker);
        }
        } catch (ReflectUtil.ClassNotFoundError ex) {
            myLog("hookSettings missing class: " + ex);
        } catch (Exception ex) {
            myLog("hookSettings error: " + ex);
        }
    }

    private void hookSystemUi(ClassLoader classLoader) {
        try {
            myLog("com.android.systemui enter");
            final String[] macrep = new String[1];

            try {
                final Class<?> MiuiKeyguardUtilsClass = ReflectUtil.findClass(
                        "com.android.keyguard.utils.MiuiKeyguardUtils", classLoader);
                systemuiR = ReflectUtil.findClass("com.android.systemui.R$string", classLoader);
                miui_keyguard_ble_unlock_succeed_msg = ReflectUtil.getStaticIntField(systemuiR, "miui_keyguard_ble_unlock_succeed_msg");

                hookMethod(ReflectUtil.findMethod(MiuiKeyguardUtilsClass, "handleBleUnlockSucceed", Context.class), chain -> {
                    if ("1".equals(ConfigUtil.getString("showtips", "1", 2))) {
                        Context ctx = (Context) chain.getArg(0);
                        String unlockstring = ctx.getResources().getString(miui_keyguard_ble_unlock_succeed_msg);
                        Toast.makeText(ctx, unlockstring, Toast.LENGTH_SHORT).show();
                    }
                    return null;
                });
            } catch (ReflectUtil.ClassNotFoundError ex) {
                Method tryUnlockByBle = ReflectUtil.findMethod(
                        ReflectUtil.findClass("com.android.keyguard.MiuiBleUnlockHelper", classLoader),
                        "tryUnlockByBle");
                Method toastMakeText = ReflectUtil.findMethod(Toast.class, "makeText", Context.class, int.class, int.class);
                Method toastShow = ReflectUtil.findMethod(Toast.class, "show");
                hookMethod(tryUnlockByBle, chain -> {
                    XposedInterface.HookHandle makeTextHandle = hook(toastMakeText).intercept(makeTextChain -> {
                        String resName = ((Context) makeTextChain.getArg(0)).getResources()
                                .getResourceName((Integer) makeTextChain.getArg(1));
                        if (Objects.equals(resName, "com.android.systemui:string/miui_keyguard_ble_unlock_succeed_msg")) {
                            XposedInterface.HookHandle showHandle = hook(toastShow).intercept(showChain -> {
                                if (!("1".equals(ConfigUtil.getString("showtips", "1", 2)))) {
                                    return null;
                                }
                                return showChain.proceed();
                            });
                            try {
                                return makeTextChain.proceed();
                            } finally {
                                showHandle.unhook();
                            }
                        }
                        return makeTextChain.proceed();
                    });
                    try {
                        return chain.proceed();
                    } finally {
                        makeTextHandle.unhook();
                    }
                });
            }

            final Class<?> BluetoothControllerImplClass = ReflectUtil.findClass(
                    "com.android.systemui.statusbar.policy.BluetoothControllerImpl", classLoader);
            hookAllConstructors(BluetoothControllerImplClass, chain -> {
                myLog("-------------- before hook BluetoothControllerImplClass ------------");
                Object result = chain.proceed();
                myLog("-------------- after hook BluetoothControllerImplClass ------------");
                BluetoothHelper.BluetoothControllerImplInstance = chain.getThisObject();
                return result;
            });

            Class<?> MiuiBleUnlockHelper = ReflectUtil.findClassIfExists(
                    "com.android.keyguard.MiuiBleUnlockHelper",
                    classLoader
            );
            myLog("-------------- hook MiuiBleUnlockHelper ------------");
            hookAllConstructors(MiuiBleUnlockHelper, chain -> {
                myLog("-------------- before hook MiuiBleUnlockHelper ------------");
                Object result = chain.proceed();
                myLog("-------------- after hook MiuiBleUnlockHelper ------------");
                try {
                    Field mBleListenerField = ReflectUtil.getFieldContainingName(MiuiBleUnlockHelper, "bleListener");
                    mBleListener = mBleListenerField.get(chain.getThisObject());

                    MyXp.classLoader = classLoader;
                    Field mLockPatternUtilsField = ReflectUtil.getFieldContainingName(MiuiBleUnlockHelper, "lockPatternUtils");
                    mLockPatternUtils = mLockPatternUtilsField.get(chain.getThisObject());

                    Class<?> clazzActivity = mBleListener.getClass();
                    hookAllMethods(clazzActivity, "onUnlocked", unlockedChain -> {
                        myLog("-------------- before hook mBleListener.onUnlocked ------------");
                        if (unlockedChain.getArgs().size() == 1
                                && "0".equals(String.valueOf(unlockedChain.getArg(0)))) {
                            CheckPhoneUnlock();
                        }
                        Object unlockedResult = unlockedChain.proceed();
                        myLog("-------------- after hook mBleListener.onUnlocked ------------");
                        return unlockedResult;
                    });
                } catch (Exception ex) {
                    myLog("-------------- MiuiBleUnlockHelper hook error ： " + ex);
                }
                return result;
            });

            final Class<?> MiuiLockPatternUtilClass = ReflectUtil.findClass(
                    "android.security.MiuiLockPatternUtils", classLoader);
            hookMethod(ReflectUtil.findMethod(Application.class, "attach", Context.class), chain -> {
                Object result = chain.proceed();
                try {
                    Context context1 = (Context) chain.getArg(0);
                    context = context1;
                    macrep[0] = ConfigUtil.getString("mac", "", 2);
                    if (macrep[0] != null && !macrep[0].isEmpty()) {
                        if (ConfigUtil.BASE_MODE.equals(macrep[0])) {
                            Object utilclass = ReflectUtil.newInstance(MiuiLockPatternUtilClass, context1);
                            ReflectUtil.callMethod(utilclass, "setBluetoothUnlockEnabled", false);
                            ReflectUtil.callMethod(utilclass, "setBluetoothAddressToUnlock", "");
                            ReflectUtil.callMethod(utilclass, "setBluetoothNameToUnlock", "");
                            ReflectUtil.callMethod(utilclass, "setBluetoothKeyToUnlock", "");
                        } else {
                            Object utilclass = ReflectUtil.newInstance(MiuiLockPatternUtilClass, context1);
                            ReflectUtil.callMethod(utilclass, "setBluetoothUnlockEnabled", true);
                            ReflectUtil.callMethod(utilclass, "setBluetoothAddressToUnlock", macrep[0]);
                            ReflectUtil.callMethod(utilclass, "setBluetoothNameToUnlock", "mibluetoothunlocker");
                            ReflectUtil.callMethod(utilclass, "setBluetoothKeyToUnlock", "mibluetoothunlocker");
                        }
                    }
                } catch (Exception ex) {
                    myLog(ex.toString());
                }
                return result;
            });
        } catch (ReflectUtil.ClassNotFoundError ex) {
            myLog(ex.toString());
        } catch (Exception ex) {
            myLog(ex.toString());
        }
        myLog("com.android.systemui leave");
    }

    public static Class<?> systemuiR = null;
    static int miui_keyguard_ble_unlock_succeed_msg;

    public static void CheckPhoneUnlock() {
        Runnable mt = () -> {
            if (context != null && mLockPatternUtils != null && classLoader != null) {
                BluetoothHelper.CanUnlockByBluetoothOldDirect(context,
                        ReflectUtil.callMethod(mLockPatternUtils, "getBluetoothAddressToUnlock").toString(),
                        classLoader, 2);
            } else {
                myLog("---------------NULL context--------------" + context + mLockPatternUtils + classLoader);
            }
        };
        Thread mt1 = new Thread(mt, "unlockthread");
        mt1.start();
        myLog("--------------mt1 unlockthread ------------");
    }

    static ClassLoader classLoader = null;
    static Object mLockPatternUtils = null;
    static Context context = null;
    static Object mUnlockListener = null;
    public static Object mBleListener = null;

    public static void UnlockPhone() {
        try {
            if (mBleListener != null && context != null) {
                new Handler(context.getMainLooper()).post(() ->
                        ReflectUtil.callMethod(mBleListener, "onUnlocked", (byte) 2));
            } else {
                myLog("---------------NULL unlockMethod--------------");
            }
        } catch (Exception e) {
            myLog(e.toString());
        }
    }

    public static void SetBluetoothStatus(byte b) {
        try {
            if (mUnlockListener != null && context != null) {
                new Handler(context.getMainLooper()).post(() ->
                        ReflectUtil.callMethod(mUnlockListener, "onUnlocked", b));
            } else {
                myLog("---------------NULL unlockMethod--------------");
            }
        } catch (Exception e) {
            myLog(e.toString());
        }
    }
}

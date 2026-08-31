package zixing.bluetooth.unlocker.utils;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import zixing.bluetooth.unlocker.Xp.MyXp;
import zixing.bluetooth.unlocker.activity.MainActivity;

public class BluetoothHelper {

    private static final boolean DEBUG_LOG = false;

    private static void myLog(String msg) {
        if (!DEBUG_LOG) {
            return;
        }
        writeLog(msg);
    }

    private static void errorLog(String msg) {
        writeLog(msg);
    }

    private static void writeLog(String msg) {
        try {
            Log.i("hookhelper", msg);
            if (MainActivity.self == null) {
                XposedBridge.log(msg);
            }
        } catch (Exception ex) {
        }
    }

    public static Object BluetoothControllerImplInstance;

    @SuppressLint("MissingPermission")
    public static boolean CanUnlockByBluetooth(Context context, String mac, ClassLoader classLoader, int type) {
        try {
            int baseRSSI = -50;
            String rssi = ConfigUtil.getString("rssi", "-50", type);
            baseRSSI = Integer.parseInt(rssi);

            myLog("-------------mac---------------" + mac + "----" + baseRSSI);

            if (BluetoothControllerImplInstance == null) {
                myLog("-------------BluetoothControllerImplInstance---------------" + BluetoothControllerImplInstance);
                return false;
            }

            final Class CachedBluetoothDeviceClass = XposedHelpers.findClass("com.android.settingslib.bluetooth.CachedBluetoothDevice", classLoader);

            ArrayList ConnectedDevices = (ArrayList) (XposedHelpers.callMethod(BluetoothControllerImplInstance, "getConnectedDevices"));
            if (ConnectedDevices == null || ConnectedDevices.size() == 0) {
                myLog("没有连接的设备");
                return false;
            }
            myLog("连接了设备" + ConnectedDevices.size() + "个设备");
            for (int i = 0; i < ConnectedDevices.size(); i++) {
                Object nowblueitem = ConnectedDevices.get(i);
                BluetoothDevice mDevice = null;
                Field mDeviceField = CachedBluetoothDeviceClass.getDeclaredField("mDevice");
                mDeviceField.setAccessible(true);
                Object mDeviceObj = mDeviceField.get(nowblueitem);
                mDevice = (BluetoothDevice) mDeviceObj;
                if (mDevice != null) {
                    String macaddress = mDevice.getAddress();
                    if (macaddress.equals(mac) == false) {
                        continue;
                    } else {
                        Field mRssiField = CachedBluetoothDeviceClass.getDeclaredField("mRssi");
                        mRssiField.setAccessible(true);
                        short mRssi = (short) mRssiField.get(nowblueitem);
                        myLog("信号强度：" + mRssi + "，信号阈值：" + baseRSSI);
                        if (mRssi >= baseRSSI) {
                            return true;
                        }
                        return false;
                    }
                }
            }
        } catch (Exception ex) {
            errorLog("发生错误：" + ex.toString());
        }
        return false;
    }

    static int baseRSSI;
    private static final AtomicBoolean scanning = new AtomicBoolean(false);

    //type1是setting，2是系统界面，0是软件本体
    @SuppressLint("MissingPermission")
    public static void CanUnlockByBluetoothOldDirect(Context context, String mac, ClassLoader classLoader, int type) {
        try {
            String mac2 = ConfigUtil.getString("mac", "", type);
            if (!ConfigUtil.BASE_MODE.equals(mac2)) {
                mac = mac2;
            }
            myLog("mac is " + mac);
            if (mac == null || mac.isEmpty()) {
                return;
            }
            String rssiCfg = ConfigUtil.getString("rssi", "-50", type);
            baseRSSI = Integer.parseInt(rssiCfg);

            if (!scanning.compareAndSet(false, true)) {
                myLog("已有扫描在进行，跳过本次");
                return;
            }
            int fastFb = fallbackRssiByConnState(context, mac.toUpperCase());
            if (fastFb != Integer.MIN_VALUE) {
                myLog("快速命中连接状态 直接判定 rssi=" + fastFb);
                scanning.set(false);
                handleResult(fastFb, type);
                return;
            }
            scanRssiAndAct(context, mac, type);
        } catch (Exception ex) {
            scanning.set(false);
            errorLog("发生错误：" + ex.toString());
        }
    }

    @SuppressLint("MissingPermission")
    private static void scanRssiAndAct(Context context, final String mac, final int type) {
        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            myLog("蓝牙未启用，放弃扫描");
            scanning.set(false);
            handleResult(Integer.MIN_VALUE, type);
            return;
        }
        final BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            myLog("LeScanner 为空，放弃扫描");
            scanning.set(false);
            handleResult(Integer.MIN_VALUE, type);
            return;
        }

        final Handler handler = new Handler(Looper.getMainLooper());
        final AtomicBoolean finished = new AtomicBoolean(false);
        final AtomicInteger bestRssi = new AtomicInteger(Integer.MIN_VALUE);

        final AtomicInteger seenCount = new AtomicInteger(0);
        final String macUpper = mac.toUpperCase();

        String targetNameTmp = null;
        try {
            BluetoothDevice bonded = adapter.getRemoteDevice(mac);
            if (bonded != null) targetNameTmp = bonded.getName();
        } catch (Exception ex) {
            errorLog("获取目标设备名异常：" + ex.toString());
        }
        final String targetName = targetNameTmp;
        myLog("目标 mac=" + macUpper + " name=" + targetName);

        final ScanCallback callback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                if (result == null || result.getDevice() == null) return;
                String addr = result.getDevice().getAddress();
                int rssi = result.getRssi();
                ScanRecord rec = result.getScanRecord();
                String advName = rec != null ? rec.getDeviceName() : null;
                List<ParcelUuid> uuids = rec != null ? rec.getServiceUuids() : null;

                int n = seenCount.incrementAndGet();
                if (n <= 30) {
                    myLog("扫到 " + addr + " rssi=" + rssi + " name=" + advName + " uuids=" + uuids);
                }

                boolean matched = false;
                String matchReason = null;
                if (addr != null && addr.equalsIgnoreCase(macUpper)) {
                    matched = true;
                    matchReason = "MAC";
                } else if (targetName != null && !targetName.isEmpty() && targetName.equals(advName)) {
                    matched = true;
                    matchReason = "NAME";
                } else if (uuids != null) {
                    for (ParcelUuid u : uuids) {
                        String s = u.getUuid().toString().toLowerCase();
                        if (s.startsWith("0000fee0") || s.startsWith("0000fee1")) {
                            matched = true;
                            matchReason = "UUID-" + s.substring(0, 8);
                            break;
                        }
                    }
                }
                if (!matched) return;

                if (rssi > bestRssi.get()) bestRssi.set(rssi);
                myLog("命中目标设备 by=" + matchReason + " addr=" + addr + " rssi=" + rssi + " 阈值=" + baseRSSI);
                if (rssi >= baseRSSI) {
                    finishScan(scanner, this, handler, finished, bestRssi.get(), type);
                }
            }

            @Override
            public void onBatchScanResults(java.util.List<ScanResult> results) {
                if (results == null) return;
                for (ScanResult r : results) onScanResult(0, r);
            }

            @Override
            public void onScanFailed(int errorCode) {
                errorLog("扫描失败 errorCode=" + errorCode);
                finishScan(scanner, this, handler, finished, Integer.MIN_VALUE, type);
            }
        };

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();

        try {
            scanner.startScan(Collections.<ScanFilter>emptyList(), settings, callback);
            myLog("LeScan 启动（无过滤诊断模式）目标=" + macUpper);
        } catch (Exception ex) {
            errorLog("startScan 异常：" + ex.toString());
            scanning.set(false);
            handleResult(Integer.MIN_VALUE, type);
            return;
        }

        final Context ctxFinal = context;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                myLog("扫描超时 累计扫到设备数=" + seenCount.get());
                int finalRssi = bestRssi.get();
                if (finalRssi == Integer.MIN_VALUE) {
                    int fb = fallbackRssiByConnState(ctxFinal, macUpper);
                    if (fb != Integer.MIN_VALUE) {
                        myLog("使用连接状态回退 rssi=" + fb);
                        finalRssi = fb;
                    }
                }
                finishScan(scanner, callback, handler, finished, finalRssi, type);
            }
        }, 2500);
    }

    @SuppressLint("MissingPermission")
    private static int fallbackRssiByConnState(Context context, String mac) {
        try {
            if (context == null) return Integer.MIN_VALUE;
            BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (bm == null || adapter == null) return Integer.MIN_VALUE;
            BluetoothDevice dev = adapter.getRemoteDevice(mac);
            if (dev == null) return Integer.MIN_VALUE;
            int gatt = bm.getConnectionState(dev, BluetoothProfile.GATT);
            int gattSrv = bm.getConnectionState(dev, BluetoothProfile.GATT_SERVER);
            int bond = dev.getBondState();

            boolean aclConnected = false;
            try {
                Object r = BluetoothDevice.class.getMethod("isConnected").invoke(dev);
                if (r instanceof Boolean) aclConnected = (Boolean) r;
            } catch (Exception ex) {
                errorLog("isConnected 反射异常: " + ex.toString());
            }

            int adapterAclState = -1;
            try {
                Object r = BluetoothAdapter.class.getMethod("getConnectionState").invoke(adapter);
                if (r instanceof Integer) adapterAclState = (Integer) r;
            } catch (Exception ex) {
            }

            myLog("回退检查 mac=" + mac + " bond=" + bond + " GATT=" + gatt + " GATT_SERVER=" + gattSrv
                    + " ACL=" + aclConnected + " adapterAcl=" + adapterAclState);
            if (gatt == BluetoothProfile.STATE_CONNECTED || gattSrv == BluetoothProfile.STATE_CONNECTED) {
                return -40;
            }
            if (aclConnected) {
                myLog("回退命中: ACL 已连接");
                return -40;
            }
            List<BluetoothDevice> connected = bm.getConnectedDevices(BluetoothProfile.GATT);
            if (connected != null) {
                for (BluetoothDevice d : connected) {
                    if (d != null && mac.equalsIgnoreCase(d.getAddress())) {
                        myLog("回退检查 在 GATT 已连接列表中找到目标");
                        return -40;
                    }
                }
            }
            List<BluetoothDevice> connectedSrv = bm.getConnectedDevices(BluetoothProfile.GATT_SERVER);
            if (connectedSrv != null) {
                for (BluetoothDevice d : connectedSrv) {
                    if (d != null && mac.equalsIgnoreCase(d.getAddress())) {
                        myLog("回退检查 在 GATT_SERVER 已连接列表中找到目标");
                        return -40;
                    }
                }
            }
        } catch (Exception ex) {
            errorLog("回退异常: " + ex.toString());
        }
        return Integer.MIN_VALUE;
    }

    @SuppressLint("MissingPermission")
    private static void finishScan(BluetoothLeScanner scanner, ScanCallback callback, Handler handler,
                                   AtomicBoolean finished, int rssi, int type) {
        if (!finished.compareAndSet(false, true)) return;
        try {
            scanner.stopScan(callback);
        } catch (Exception ex) {
            errorLog("stopScan 异常：" + ex.toString());
        }
        handler.removeCallbacksAndMessages(null);
        scanning.set(false);
        handleResult(rssi, type);
    }

    private static void handleResult(int rssi, int type) {
        myLog("扫描结束 rssi=" + rssi + " 阈值=" + baseRSSI + " type=" + type);
        if (type == 1) {
            if (rssi != Integer.MIN_VALUE && rssi >= baseRSSI) {
                MyXp.SetBluetoothStatus((byte) 2);
                myLog("手环状态-成功");
            } else {
                MyXp.SetBluetoothStatus((byte) 1);
                myLog("手环状态-距离太远或未发现");
            }
        } else if (type == 2) {
            if (rssi != Integer.MIN_VALUE && rssi >= baseRSSI) {
                MyXp.UnlockPhone();
                myLog("已解锁手机");
            } else {
                myLog("不符合解锁条件");
            }
        }
    }
}

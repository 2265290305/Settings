package com.cloudsteem.autobluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AutoBlueToothManager {

    public static final String TAG = "AutoBlueToothManager";

    private Context mContext;
    private BluetoothAdapter mBtAdapter;
    private BluetoothProfile mService;
    private boolean mIsBound = false; // 是否正在配对
    private boolean mIsScanning = false;
    private boolean mHasRemoteConnected = false;

    private CountDownTimer mCountDownTimer;
    private DeviceCallback mCallback;
    private final List<BluetoothDevice> mScannedDevices = new ArrayList<>();
    private boolean mManualMode = false;
    private boolean mReceiverRegistered = false;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    public interface DeviceCallback {
        void onScanResult(BluetoothDevice device, int rssi);
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected(BluetoothDevice device);
        void onBondRemoved(BluetoothDevice device);
        void onScanStarted();
        void onScanFinished();
        void onServiceReady();
    }

    public void setDeviceCallback(DeviceCallback callback) {
        this.mCallback = callback;
    }

    public AutoBlueToothManager(Context context) {
        Log.d(TAG, "AutoBlueToothManager 构造函数====");
        this.mContext = context.getApplicationContext();
        mCountDownTimer = new CountDownTimer(Integer.MAX_VALUE, 8000) {
            @Override
            public void onTick(long millisUntilFinished) {
                Log.d(TAG, "定时器：是否正在扫描=" + mIsScanning + ", 是否连接了遥控器=" + mHasRemoteConnected);
                if(!mHasRemoteConnected){
                    if (!mIsScanning) {
                        Log.d(TAG, "test log");
                        enableLeScan(true);
                    }
                }else {
                    Log.d(TAG, "test log stopScan");
                    stopScan();
                }
                
            }

            @Override
            public void onFinish() {
                // 循环定时器，不会自然结束
            }
        };
    }

    public void start() {
        Log.d(TAG, "start...");
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBtAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙");
            return;
        }
        registerReceivers();
        mBtAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.HID_HOST);
        if (!mBtAdapter.isEnabled()) {
            mBtAdapter.enable();
        }
        mCountDownTimer.start();
    }

    public void startManualMode() {
        Log.d(TAG, "startManualMode...");
        mManualMode = true;
        if (mBtAdapter == null) {
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (mBtAdapter == null) {
            Log.e(TAG, "设备不支持蓝牙");
            return;
        }
        if (mService == null) {
            mBtAdapter.getProfileProxy(mContext, mListener, BluetoothProfile.HID_HOST);
        }
        registerReceivers();
        if (!mBtAdapter.isEnabled()) {
            mBtAdapter.enable();
        }
        mScannedDevices.clear();
    }

    public void stopManualMode() {
        Log.d(TAG, "stopManualMode...");
        mManualMode = false;
        enableLeScan(false);
        mScannedDevices.clear();
        closeProfileProxy();
    }

    public void startScan() {
        mScannedDevices.clear();
        enableLeScan(true);
    }

    public void stopScan() {
        enableLeScan(false);
    }

    public void release() {
        Log.d(TAG, "release...");
        mCountDownTimer.cancel();
        enableLeScan(false);
        closeProfileProxy();
        unregisterReceivers();
        mScannedDevices.clear();
        mCallback = null;
        mHasRemoteConnected = false;
        mIsBound = false;
    }

    private void registerReceivers() {
        if (mReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mReceiver, filter);
        mReceiverRegistered = true;
    }

    private void unregisterReceivers() {
        if (!mReceiverRegistered) {
            return;
        }
        try {
            mContext.unregisterReceiver(mReceiver);
        } catch (Exception e) {
            Log.w(TAG, "unregisterReceiver error: " + e.getMessage());
        }
        mReceiverRegistered = false;
    }

    private void closeProfileProxy() {
        if (mBtAdapter != null && mService != null) {
            try {
                mBtAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mService);
            } catch (Exception e) {
                Log.w(TAG, "closeProfileProxy error: " + e.getMessage());
            }
            mService = null;
        }
    }

    public List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> connected = new ArrayList<>();
        if (mBtAdapter == null || mService == null) return connected;
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (mService.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                if (judgeRC(device)) {
                    connected.add(device);
                }
            }
        }
        return connected;
    }

    public List<BluetoothDevice> getBondedRCDevices() {
        List<BluetoothDevice> bonded = new ArrayList<>();
        if (mBtAdapter == null) return bonded;
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (judgeRC(device)) {
                bonded.add(device);
            }
        }
        return bonded;
    }

    public void connectDevice(BluetoothDevice device) {
        connect(device);
    }

    public void removeBond(BluetoothDevice device) {
        unpairDevice(device);
    }

    private boolean hasConnectedDevice() {
        if (mBtAdapter == null || mService == null) return false;
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (mService.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "has connected");
                return true;
            }
        }
        Log.d(TAG, "no connected");
        return false;
    }

    /**
     * 检查是否存在已配对的遥控器。
     * 注意：该方法只读，不再执行删除或重启蓝牙等破坏性操作。
     */
    private boolean hasBondedDevice() {
        if (mBtAdapter == null) return false;
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName() != null && judgeRC(device)) {
                Log.d(TAG, "hasBondedDevice true: " + device.getName());
                return true;
            }
        }
        Log.d(TAG, "hasBondedDevice false");
        return false;
    }

    private void onDeviceFound(BluetoothDevice bluetoothDevice, int rssi) {
        // 手动/扫描模式：所有设备都回调给 UI 展示；自动模式：只处理 RC 遥控器
        if (mManualMode) {
            Log.d(TAG, "###发现设备#### name=" + bluetoothDevice.getName()
                    + " bondState=" + bluetoothDevice.getBondState()
                    + " rssi=" + rssi);
            if (!mScannedDevices.contains(bluetoothDevice)) {
                mScannedDevices.add(bluetoothDevice);
            }
            if (mCallback != null) {
                mCallback.onScanResult(bluetoothDevice, rssi);
            }
            return; // 手动模式只展示扫描结果，不自动配对/连接
        }

        if (!judgeRC(bluetoothDevice)) {
            return;
        }
        Log.d(TAG, "###发现遥控器#### name=" + bluetoothDevice.getName()
                + " bondState=" + bluetoothDevice.getBondState()
                + " rssi=" + rssi);
        if (!mScannedDevices.contains(bluetoothDevice)) {
            mScannedDevices.add(bluetoothDevice);
        }
        if (mCallback != null) {
            mCallback.onScanResult(bluetoothDevice, rssi);
        }

        // 自动模式：发现未配对遥控器时尝试配对并连接
        if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_NONE) {
            enableLeScan(false);
            if (hasBondedDevice()) {
                Log.d(TAG, ">>>>>>>>>>>已存在配对的遥控器，暂不处理新设备");
                return;
            }
            Log.d(TAG, "start to createBond......");
            createBond(bluetoothDevice);

            Log.d(TAG, "start to connect......");
            // 延迟等待配对完成后再连接
            mMainHandler.postDelayed(() -> {
                if (mService != null
                        && mService.getConnectionState(bluetoothDevice) == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "尝试连接设备: " + bluetoothDevice.getName());
                    connect(bluetoothDevice);
                }
            }, 1500);
        } else if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
            mIsBound = false;
        } else if (bluetoothDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            // 已配对但未连接，尝试连接
            if (mService != null
                    && mService.getConnectionState(bluetoothDevice) == BluetoothProfile.STATE_DISCONNECTED) {
                connect(bluetoothDevice);
            }
        }
    }

    @SuppressLint("NewApi")
    private BluetoothProfile.ServiceListener mListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            try {
                if (profile == BluetoothProfile.HID_HOST) {
                    mService = proxy;
                    Log.d(TAG, "###BluetoothProfile 服务已连接: " + mService);
                    // 服务就绪后，若当前已连接指定遥控器则更新状态
                    mHasRemoteConnected = hasRemoteConnectedDevice();
                    if (mCallback != null) {
                        mCallback.onServiceReady();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "onServiceConnected error: " + e.getMessage());
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            Log.d(TAG, "Bluetooth service disconnected");
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;
            Log.d(TAG, "#####[广播]" + action);

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice bluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (bluetoothDevice == null) return;
                Log.d(TAG, "bluetoothDevice name: " + bluetoothDevice.getName());
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                onDeviceFound(bluetoothDevice, Math.abs(rssi));
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice bd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (bd == null) return;
                Log.d(TAG, "#####配对状态变更##### state: " + bd.getBondState());
                switch (bd.getBondState()) {
                    case BluetoothDevice.BOND_BONDED:
                        mIsBound = false;
                        Log.d(TAG, "配对成功#####");
                        if (mCallback != null) {
                            mCallback.onDeviceConnected(bd);
                        }
                        if (mService != null) {
                            int connectionState = mService.getConnectionState(bd);
                            Log.d(TAG, "配对成功后连接状态: " + connectionState);
                            if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
                                connect(bd);
                            }
                        }
                        break;
                    case BluetoothDevice.BOND_NONE:
                        mIsBound = false;
                        Log.d(TAG, "配对失败/解除配对#####");
                        unpairDevice(bd);
                        if (mCallback != null) {
                            mCallback.onBondRemoved(bd);
                        }
                        break;
                    case BluetoothDevice.BOND_BONDING:
                        mIsBound = true;
                        Log.d(TAG, "配对ing#####");
                        break;
                }
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                Log.d(TAG, "#####蓝牙状态变更##### state=" + state);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        enableLeScan(true);
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        mHasRemoteConnected = false;
                        break;
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice bd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                Log.d(TAG, "[ACTION_ACL_DISCONNECTED] 断开连接");
                if (bd != null && judgeRC(bd)) {
                    mHasRemoteConnected = false;
                    if (mCallback != null) {
                        mCallback.onDeviceDisconnected(bd);
                    }
                }
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice bd = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mIsBound = true;
                Log.d(TAG, "[ACTION_ACL_CONNECTED] 发起连接 " + (bd != null ? bd.getName() : "null"));
                if (bd != null && judgeRC(bd)) {
                    mHasRemoteConnected = true;
                    if (mCallback != null) {
                        mCallback.onDeviceConnected(bd);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mIsScanning = true;
                Log.d(TAG, "######开始搜索####");
                if (mCallback != null) {
                    Log.d(TAG, "onScanStarted CallBack != null");
                    mCallback.onScanStarted();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mIsScanning = false;
                Log.d(TAG, "######搜索结束####");
                if (mCallback != null) {
                    Log.d(TAG, "onScanFinished CallBack != null");
                    mCallback.onScanFinished();
                }
            }
        }
    };

    public void enableLeScan(boolean enable) {
        Log.d(TAG, "##########enableLeScan: " + enable);
        if (mBtAdapter == null) return;
        if (enable) {
            if (!hasRemoteConnectedDevice()) {
                mBtAdapter.startDiscovery();
            } else {
                mBtAdapter.cancelDiscovery();
            }
        } else {
            mBtAdapter.cancelDiscovery();
        }
    }

    public void connect(BluetoothDevice device) {
        Log.i(TAG, "尝试连接设备: " + device.getName());
        if (mBtAdapter != null) {
            mBtAdapter.cancelDiscovery();
        }
        if (mService == null) {
            Log.e(TAG, "mService 未初始化，无法连接");
            return;
        }
        try {
            if (mService.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "设备已连接，无需重复连接");
                mHasRemoteConnected = true;
                return;
            }
            Method method = mService.getClass().getMethod("connect", BluetoothDevice.class);
            boolean success = (boolean) method.invoke(mService, device);
            Log.d(TAG, "连接结果: " + (success ? "成功" : "失败"));
            if (!success) {
                mMainHandler.postDelayed(() -> connect(device), 2000);
            }
        } catch (Exception e) {
            Log.e(TAG, "连接异常: " + e.getMessage());
        }
    }

    public void createBond(BluetoothDevice device) {
        if (mIsBound) {
            Log.w(TAG, "正在配对中，忽略新的配对请求");
            return;
        }
        try {
            boolean result = createBond(device.getClass(), device);
            Log.d(TAG, "[createBond] result: " + result);
        } catch (Exception e) {
            Log.e(TAG, "[createBond] 配对异常: " + e.getMessage());
        }
    }

    private boolean createBond(Class<?> btClass, BluetoothDevice btDevice) throws Exception {
        Method createBondMethod = btClass.getMethod("createBond");
        return (boolean) createBondMethod.invoke(btDevice);
    }

    public void unpairDevice(BluetoothDevice device) {
        Log.e(TAG, "[unpairDevice]删除配对设备!");
        try {
            Method m = device.getClass().getMethod("removeBond");
            m.invoke(device);
        } catch (Exception e) {
            Log.e(TAG, "removeBond error: " + e.getMessage());
        }
    }

    public boolean judgeRC(BluetoothDevice device) {
        if (device == null || device.getName() == null) return false;
        String name = device.getName();
        return name.contains("RC-01") || name.contains("RC-03") || name.contains("电信蓝牙遥控");
    }

    // 是否有指定遥控器连接着
    public boolean hasRemoteConnectedDevice() {
        Log.d(TAG, "Fun() hasRemoteConnectedDevice");
        if (mBtAdapter == null) return false;
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (mService != null
                    && mService.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "has connected: " + device.getName());
                if (judgeRC(device)) {
                    mHasRemoteConnected = true;
                    return true;
                }
            }
        }
        Log.d(TAG, "no connected");
        mHasRemoteConnected = false;
        return false;
    }
}

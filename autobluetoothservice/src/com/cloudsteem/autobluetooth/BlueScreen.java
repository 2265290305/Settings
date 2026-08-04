package com.cloudsteem.autobluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BlueScreen extends Activity {

    private static final String BLUE_SCREEN_TAG = "BlueScreen";
    private static final String IGNORED_BLUETOOTH_PREFS = "ignored_bluetooth_devices";
    private static final String IGNORED_BLUETOOTH_ADDRESSES = "ignored_addresses";
    private static final String DISCONNECTED_BLUETOOTH_ADDRESSES = "disconnected_addresses";

    private BluetoothManager mBlm;
    private BluetoothAdapter mAdapter;
    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private SharedPreferences mIgnoredPrefs;

    private final Set<String> mForgettingAddresses = new HashSet<>();
    private final Set<String> mBlockedAutoReconnectAddresses = new HashSet<>();
    private final Map<String, Long> mIgnoredAtMs = new HashMap<>();

    private final List<BluetoothDevice> mPairedDevices = new ArrayList<>();
    private final List<BluetoothDevice> mDiscoveredClassicDevices = new ArrayList<>();
    private final List<BluetoothDevice> mCachedBleDevices = new ArrayList<>();
    private final List<String> mConnectedAddresses = new ArrayList<>();
    private final Map<String, String> mDeviceNameCache = new HashMap<>();

    private BluetoothProfile mHidHostProxy;
    private BluetoothGatt mActiveGatt;
    private BluetoothDevice mConnectedDevice;
    private BluetoothDevice mBleConnectedDevice;
    private String mPendingBleConnectAddress;
    private BluetoothDevice mPendingPairDevice;
    private String mBleConnectingAddress;
    private String mAutoConnectingAddress;
    private String mManualDisconnectAddress;
    private String mStickyConnectedRemoteAddress;
    private long mStickyConnectedUntilMs;
    private int mConnectStateTick;

    private boolean mIsScanning = false;
    private boolean mShowRenameDialog = false;
    private boolean mShowBleRemoteDialog = false;
    private boolean mShowBlePairingTip = false;
    private boolean mShowBleDisconnectTip = false;
    private boolean mShowDeviceOptionsDialog = false;
    private BluetoothDevice mSelectedDevice;
    private PairingRequestInfo mPairingRequest;
    private BluetoothDevice mInteractivePairRequest;

    private String mBtName = "";
    private ScanCallback mBleScanCallback;

    private DeviceAdapter mPairedAdapter;
    private DeviceAdapter mAvailableAdapter;
    private DeviceAdapter mRemoteAdapter;

    private static final int REQUEST_PERMISSIONS = 2001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blue_screen);

        mBlm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mAdapter = mBlm != null ? mBlm.getAdapter() : null;
        mIgnoredPrefs = getSharedPreferences(IGNORED_BLUETOOTH_PREFS, Context.MODE_PRIVATE);

        loadIgnoredAddresses();
        loadBlockedAutoReconnectAddresses();

        initViews();

        checkAndRequestPermissions();
    }

    private void loadIgnoredAddresses() {
        Set<String> set = mIgnoredPrefs.getStringSet(IGNORED_BLUETOOTH_ADDRESSES, new HashSet<>());
        mForgettingAddresses.clear();
        mForgettingAddresses.addAll(set);
    }

    private void loadBlockedAutoReconnectAddresses() {
        Set<String> set = mIgnoredPrefs.getStringSet(DISCONNECTED_BLUETOOTH_ADDRESSES, new HashSet<>());
        mBlockedAutoReconnectAddresses.clear();
        mBlockedAutoReconnectAddresses.addAll(set);
    }

    private void persistIgnoredAddresses() {
        mIgnoredPrefs.edit().putStringSet(IGNORED_BLUETOOTH_ADDRESSES, new HashSet<>(mForgettingAddresses)).apply();
    }

    private void persistBlockedAutoReconnectAddresses() {
        mIgnoredPrefs.edit().putStringSet(DISCONNECTED_BLUETOOTH_ADDRESSES, new HashSet<>(mBlockedAutoReconnectAddresses)).apply();
    }

    private void initViews() {
        ListView lvPaired = findViewById(R.id.lv_paired);
        ListView lvAvailable = findViewById(R.id.lv_available);

        mPairedAdapter = new DeviceAdapter(mPairedDevices, true);
        mAvailableAdapter = new DeviceAdapter(mAvailableDevices(), false);

        lvPaired.setAdapter(mPairedAdapter);
        lvAvailable.setAdapter(mAvailableAdapter);

        lvPaired.setOnItemClickListener((parent, view, position, id) -> {
            mSelectedDevice = mPairedDevices.get(position);
            showDeviceOptionsDialog(mSelectedDevice);
        });

        lvAvailable.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = mAvailableAdapter.getItem(position);
            connectDevice(device, true, true);
        });

        findViewById(R.id.switch_bluetooth).setOnClickListener(v -> {
            boolean checked = ((android.widget.Switch) v).isChecked();
            if (mAdapter != null) {
                if (checked) mAdapter.enable();
                else mAdapter.disable();
            }
        });

        findViewById(R.id.tv_rename).setOnClickListener(v -> showRenameDialog());
        findViewById(R.id.tv_remote).setOnClickListener(v -> showBleRemoteDialog());
        findViewById(R.id.btn_refresh).setOnClickListener(v -> startScan());
    }

    private List<BluetoothDevice> mAvailableDevices() {
        Set<String> myAddresses = new HashSet<>();
        for (BluetoothDevice d : mPairedDevices) myAddresses.add(d.getAddress());
        if (mConnectedDevice != null) myAddresses.add(mConnectedDevice.getAddress());
        if (mBleConnectedDevice != null) myAddresses.add(mBleConnectedDevice.getAddress());

        List<BluetoothDevice> all = new ArrayList<>();
        all.addAll(mDiscoveredClassicDevices);
        all.addAll(mCachedBleDevices);

        List<BluetoothDevice> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (BluetoothDevice d : all) {
            String addr = d.getAddress();
            if (!hasDisplayableName(d)) continue;
            if (mForgettingAddresses.contains(addr) && d.getBondState() != BluetoothDevice.BOND_NONE) continue;
            if (d.getBondState() == BluetoothDevice.BOND_BONDED) continue;
            if (myAddresses.contains(addr)) continue;
            if (added.contains(addr)) continue;
            added.add(addr);
            result.add(d);
        }
        result.sort((a, b) -> Boolean.compare(isTelecomRemote(b), isTelecomRemote(a)));
        return result;
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED)
                permissions.add("android.permission.BLUETOOTH_SCAN");
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED)
                permissions.add("android.permission.BLUETOOTH_CONNECT");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else {
            onPermissionsReady();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                onPermissionsReady();
            } else {
                Toast.makeText(this, "缺少蓝牙或位置权限", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void onPermissionsReady() {
        if (mAdapter == null) return;
        registerReceiver();
        getHidHostProxy();
        updatePairedDevices();
        updateConnectedDevice();
        startScan();
    }

    private void getHidHostProxy() {
        if (mAdapter == null) return;
        mAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == 4) { // HID_HOST
                    mHidHostProxy = proxy;
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (profile == 4) {
                    mHidHostProxy = null;
                }
            }
        }, 4);
    }

    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action == null) return;

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String extraName = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                if (extraName != null && !extraName.trim().isEmpty() && device != null) {
                    mDeviceNameCache.put(device.getAddress(), extraName.trim());
                }
                if (device != null) {
                    addDiscoveredDevice(device);
                    autoConnectAdvertisingRemote(device);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mIsScanning = false;
                refreshUi();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                mIsScanning = true;
                refreshUi();
            } else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null || isIgnoredAddress(device.getAddress())) return;
                int variant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
                int key = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);
                try {
                    abortBroadcast();
                } catch (Exception ignored) {}
                String passkey = key != BluetoothDevice.ERROR ? String.format(Locale.US, "%06d", key) : null;
                mPairingRequest = new PairingRequestInfo(device, variant, passkey);
                showPairingDialog();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                if (mPairingRequest != null && mPairingRequest.device != null && device != null
                        && mPairingRequest.device.getAddress().equals(device.getAddress())
                        && state != BluetoothDevice.BOND_BONDING) {
                    mPairingRequest = null;
                }
                if (state == BluetoothDevice.BOND_NONE && device != null) {
                    removeDeviceFromUi(device);
                    updatePairedDevices();
                } else if (state == BluetoothDevice.BOND_BONDING) {
                    if (device != null && isIgnoredAddress(device.getAddress())) {
                        cancelPairingState(device);
                        removeDeviceFromUi(device);
                        updatePairedDevices();
                    }
                } else if (state == BluetoothDevice.BOND_BONDED && device != null) {
                    if (isIgnoredAddress(device.getAddress())) {
                        cancelPairingState(device);
                        setConnectionPolicyCompat(device, false);
                        disconnectDevice(device);
                        removeBondCompat(device);
                        removeDeviceFromUi(device);
                        updatePairedDevices();
                    } else if (mPendingBleConnectAddress != null && mPendingBleConnectAddress.equals(device.getAddress())) {
                        mShowBleDisconnectTip = false;
                        mShowBlePairingTip = true;
                        mMainHandler.postDelayed(() -> mShowBlePairingTip = false, 2200);
                        mPendingBleConnectAddress = null;
                        mPendingPairDevice = null;
                        connectDevice(device, true, false);
                    } else {
                        maybeAutoConnectRemote(device);
                    }
                }
                refreshUi();
            } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && isIgnoredAddress(device.getAddress())) {
                    setConnectionPolicyCompat(device, false);
                    disconnectDevice(device);
                    removeDeviceFromUi(device);
                    updatePairedDevices();
                    return;
                }
                if (device != null && isAutoReconnectBlocked(device.getAddress())) {
                    disconnectDevice(device);
                    removeDeviceFromUi(device);
                    updatePairedDevices();
                    return;
                }
                if (device != null && device.getAddress().equals(mBleConnectingAddress)) {
                    mBleConnectingAddress = null;
                }
                if (device != null && isLikelyRemote(device)) {
                    mMainHandler.postDelayed(() -> {
                        if (isDeviceConnectedNow(device)) {
                            markConnected(device);
                        } else {
                            markDisconnected(device);
                            connectHidProfile(device);
                        }
                        updatePairedDevices();
                        refreshUi();
                    }, 1200);
                } else {
                    markConnected(device);
                }
                updatePairedDevices();
                refreshUi();
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getAddress().equals(mManualDisconnectAddress)) {
                    mManualDisconnectAddress = null;
                }
                boolean wasConnected = device != null && mConnectedAddresses.contains(device.getAddress());
                markDisconnected(device);
                updatePairedDevices();
                mMainHandler.postDelayed(() -> {
                    updatePairedDevices();
                    refreshUi();
                }, 1500);
                if (mShowBleRemoteDialog && device != null && wasConnected && isTelecomRemote(device)) {
                    mShowBlePairingTip = false;
                    mMainHandler.postDelayed(() -> {
                        mShowBleDisconnectTip = true;
                        mMainHandler.postDelayed(() -> mShowBleDisconnectTip = false, 2500);
                    }, 500);
                }
                refreshUi();
            }
        }
    };

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mBluetoothReceiver, filter);
    }

    private void unregisterReceiverSafe() {
        try {
            unregisterReceiver(mBluetoothReceiver);
        } catch (Exception ignored) {}
    }

    private void startScan() {
        mDiscoveredClassicDevices.clear();
        mCachedBleDevices.clear();
        mIsScanning = true;
        if (mAdapter != null) {
            if (mAdapter.isDiscovering()) mAdapter.cancelDiscovery();
            mAdapter.startDiscovery();
            startBleScan();
        }
        mMainHandler.removeCallbacks(mScanTimeoutRunnable);
        mMainHandler.postDelayed(mScanTimeoutRunnable, 8000);
    }

    private final Runnable mScanTimeoutRunnable = this::stopScan;

    @SuppressLint("MissingPermission")
    private void startBleScan() {
        if (mAdapter == null || mAdapter.getBluetoothLeScanner() == null) return;
        mBleScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice scannedDevice = result.getDevice();
                if (scannedDevice == null) return;
                String advName = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
                if (advName != null && !advName.trim().isEmpty()) {
                    mDeviceNameCache.put(scannedDevice.getAddress(), advName.trim());
                }
                addDiscoveredDevice(scannedDevice);
                autoConnectAdvertisingRemote(scannedDevice);
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.w(BLUE_SCREEN_TAG, "ble scan failed error=" + errorCode);
            }
        };
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        mAdapter.getBluetoothLeScanner().startScan(null, settings, mBleScanCallback);
    }

    private void stopScan() {
        if (mAdapter != null) {
            if (mAdapter.isDiscovering()) mAdapter.cancelDiscovery();
            if (mBleScanCallback != null && mAdapter.getBluetoothLeScanner() != null) {
                mAdapter.getBluetoothLeScanner().stopScan(mBleScanCallback);
            }
        }
        mMainHandler.removeCallbacks(mScanTimeoutRunnable);
        mIsScanning = false;
        refreshUi();
    }

    private void refreshUi() {
        mMainHandler.post(() -> {
            updatePairedDevices();
            mPairedAdapter.notifyDataSetChanged();
            mAvailableAdapter.setDevices(mAvailableDevices());
            mAvailableAdapter.notifyDataSetChanged();
        });
    }

    private void cacheDeviceName(BluetoothDevice device) {
        String name = device.getName();
        if (name != null && !name.trim().isEmpty()) {
            mDeviceNameCache.put(device.getAddress(), name.trim());
        }
    }

    private String resolvedDeviceName(BluetoothDevice device) {
        String cached = mDeviceNameCache.get(device.getAddress());
        if (cached != null && !cached.trim().isEmpty()) return cached;
        String direct = device.getName();
        if (direct != null && !direct.trim().isEmpty()) return direct;
        return null;
    }

    private boolean hasDisplayableName(BluetoothDevice device) {
        return resolvedDeviceName(device) != null;
    }

    private String displayDeviceName(BluetoothDevice device) {
        String name = resolvedDeviceName(device);
        return name != null ? name : "未知设备";
    }

    private boolean isTelecomRemote(BluetoothDevice device) {
        String name = displayDeviceName(device);
        return name.contains("RC-01") || name.contains("RC-03") || name.contains("电信蓝牙遥控");
    }

    private boolean isLikelyRemote(BluetoothDevice device) {
        String display = displayDeviceName(device).toLowerCase();
        List<String> keywords = Arrays.asList("remote", "remoter", "controller", "rc-01", "rc-03",
                "dlife-rc1002", "cmcc_voice_remote", "遥控", "蓝牙遥控", "电信蓝牙遥控");
        for (String kw : keywords) {
            if (display.contains(kw)) return true;
        }
        if (device.getBluetoothClass() != null) {
            int devClass = device.getBluetoothClass().getDeviceClass();
            if ((devClass & 0x0500) == 0x0500) return true;
        }
        return false;
    }

    private boolean isBleDevice(BluetoothDevice device) {
        return device.getType() == BluetoothDevice.DEVICE_TYPE_LE
                || device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL;
    }

    private boolean isDeviceConnectedNow(BluetoothDevice device) {
        String address = device.getAddress();
        if (mHidHostProxy != null) {
            try {
                int state = mHidHostProxy.getConnectionState(device);
                if (state == BluetoothProfile.STATE_CONNECTED) return true;
            } catch (Exception ignored) {}
        }
        if (mConnectedAddresses.contains(address)) return true;
        if (mConnectedDevice != null && mConnectedDevice.getAddress().equals(address)) return true;
        if (mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(address)) return true;
        return isConnectedReflect(device);
    }

    private boolean isConnectedReflect(BluetoothDevice device) {
        try {
            Method m = device.getClass().getDeclaredMethod("isConnected");
            return (boolean) m.invoke(device);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDeviceConnectedForUi(BluetoothDevice device) {
        if (isIgnoredAddress(device.getAddress()) || isAutoReconnectBlocked(device.getAddress())) return false;
        return isDeviceConnectedNow(device);
    }

    private boolean isDeviceConnectedLive(BluetoothDevice device) {
        if (mHidHostProxy != null) {
            try {
                if (mHidHostProxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true;
            } catch (Exception ignored) {}
        }
        return isConnectedReflect(device);
    }

    private void markConnected(BluetoothDevice device) {
        if (device == null) return;
        String address = device.getAddress();
        if (!mConnectedAddresses.contains(address)) mConnectedAddresses.add(address);
        if (mAutoConnectingAddress != null && mAutoConnectingAddress.equals(address)) mAutoConnectingAddress = null;
        if (mManualDisconnectAddress != null && mManualDisconnectAddress.equals(address)) mManualDisconnectAddress = null;
        if (isTelecomRemote(device)) {
            mStickyConnectedRemoteAddress = address;
            mStickyConnectedUntilMs = 0;
        }
    }

    private void markDisconnected(BluetoothDevice device) {
        if (device == null) return;
        String address = device.getAddress();
        mConnectedAddresses.remove(address);
        if (mConnectedDevice != null && mConnectedDevice.getAddress().equals(address)) mConnectedDevice = null;
        if (mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(address)) mBleConnectedDevice = null;
        if (mAutoConnectingAddress != null && mAutoConnectingAddress.equals(address)) mAutoConnectingAddress = null;
        if (isTelecomRemote(device)) {
            if (address.equals(mManualDisconnectAddress) || !address.equals(mStickyConnectedRemoteAddress)) {
                if (address.equals(mStickyConnectedRemoteAddress)) {
                    mStickyConnectedRemoteAddress = null;
                    mStickyConnectedUntilMs = 0;
                }
            } else {
                mStickyConnectedUntilMs = SystemClock.elapsedRealtime() + 5000;
                mMainHandler.postDelayed(() -> mConnectStateTick++, 5200);
            }
        }
    }

    private void addClassicDevice(BluetoothDevice device) {
        if (!hasDisplayableName(device)) return;
        int index = -1;
        for (int i = 0; i < mDiscoveredClassicDevices.size(); i++) {
            if (mDiscoveredClassicDevices.get(i).getAddress().equals(device.getAddress())) {
                index = i;
                break;
            }
        }
        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            if (index >= 0) mDiscoveredClassicDevices.remove(index);
            return;
        }
        if (index >= 0) mDiscoveredClassicDevices.set(index, device);
        else mDiscoveredClassicDevices.add(device);
        refreshUi();
    }

    private void addBleDeviceToCache(BluetoothDevice device) {
        if (!hasDisplayableName(device)) return;
        int index = -1;
        for (int i = 0; i < mCachedBleDevices.size(); i++) {
            if (mCachedBleDevices.get(i).getAddress().equals(device.getAddress())) {
                index = i;
                break;
            }
        }
        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            if (index >= 0) mCachedBleDevices.remove(index);
            return;
        }
        if (index >= 0) mCachedBleDevices.set(index, device);
        else mCachedBleDevices.add(device);
        refreshUi();
    }

    private void addDiscoveredDevice(BluetoothDevice device) {
        if (isIgnoredAddress(device.getAddress()) && device.getBondState() != BluetoothDevice.BOND_NONE) return;
        cacheDeviceName(device);
        if (isBleDevice(device)) addBleDeviceToCache(device);
        else addClassicDevice(device);
    }

    private void updatePairedDevices() {
        if (mAdapter == null) return;
        Set<BluetoothDevice> bonded = mAdapter.getBondedDevices();
        mPairedDevices.clear();
        if (bonded != null) {
            for (BluetoothDevice device : bonded) {
                if (!mForgettingAddresses.contains(device.getAddress()) && hasDisplayableName(device)) {
                    mPairedDevices.add(device);
                }
                if (isIgnoredAddress(device.getAddress()) || isAutoReconnectBlocked(device.getAddress())) {
                    markDisconnected(device);
                } else if (isDeviceConnectedLive(device)) {
                    markConnected(device);
                } else {
                    markDisconnected(device);
                }
            }
        }
    }

    private void updateConnectedDevice() {
        if (mAdapter == null) return;
        mAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.A2DP) {
                    for (BluetoothDevice device : proxy.getConnectedDevices()) {
                        if (isIgnoredAddress(device.getAddress()) || isAutoReconnectBlocked(device.getAddress())) {
                            markDisconnected(device);
                            continue;
                        }
                        mConnectedDevice = device;
                        cacheDeviceName(device);
                        markConnected(device);
                    }
                    mAdapter.closeProfileProxy(profile, proxy);
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {}
        }, BluetoothProfile.A2DP);

        mAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                for (BluetoothDevice device : proxy.getConnectedDevices()) {
                    if (isIgnoredAddress(device.getAddress()) || isAutoReconnectBlocked(device.getAddress())) {
                        markDisconnected(device);
                        continue;
                    }
                    markConnected(device);
                }
                mAdapter.closeProfileProxy(profile, proxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {}
        }, 4);
    }

    private void disconnectDevice(BluetoothDevice device) {
        String address = device.getAddress();
        mManualDisconnectAddress = address;
        markDisconnected(device);
        if (mConnectedDevice != null && mConnectedDevice.getAddress().equals(address)) mConnectedDevice = null;
        if (mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(address)) mBleConnectedDevice = null;
        if (mAutoConnectingAddress != null && mAutoConnectingAddress.equals(address)) mAutoConnectingAddress = null;
        if (mBleConnectingAddress != null && mBleConnectingAddress.equals(address)) mBleConnectingAddress = null;
        addAutoReconnectBlockedAddress(address);
        setConnectionPolicyCompat(device, false);
        if (isLikelyRemote(device)) {
            Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show();
        }
        if (isBleDevice(device)) {
            if ((mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(address))
                    || (mActiveGatt != null && mActiveGatt.getDevice() != null && mActiveGatt.getDevice().getAddress().equals(address))) {
                if (mActiveGatt != null) mActiveGatt.disconnect();
            }
        }
        if (mAdapter == null) return;
        for (int profileId : new int[]{4, BluetoothProfile.A2DP, BluetoothProfile.HEADSET}) {
            mAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        Method method = proxy.getClass().getMethod("disconnect", BluetoothDevice.class);
                        method.setAccessible(true);
                        method.invoke(proxy, device);
                    } catch (Exception e) {
                        Log.d(BLUE_SCREEN_TAG, "disconnect profile failed: " + e.getMessage());
                    }
                    mMainHandler.postDelayed(() -> mAdapter.closeProfileProxy(profile, proxy), 1000);
                }

                @Override
                public void onServiceDisconnected(int profile) {}
            }, profileId);
        }
    }

    private void removeDeviceFromUi(BluetoothDevice device) {
        String address = device.getAddress();
        mPairedDevices.removeIf(d -> d.getAddress().equals(address));
        mDiscoveredClassicDevices.removeIf(d -> d.getAddress().equals(address));
        mCachedBleDevices.removeIf(d -> d.getAddress().equals(address));
        mConnectedAddresses.remove(address);
        if (mConnectedDevice != null && mConnectedDevice.getAddress().equals(address)) mConnectedDevice = null;
        if (mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(address)) mBleConnectedDevice = null;
        if (mPendingPairDevice != null && mPendingPairDevice.getAddress().equals(address)) {
            mPendingPairDevice = null;
            mPendingBleConnectAddress = null;
            mShowBlePairingTip = false;
        }
        if (mAutoConnectingAddress != null && mAutoConnectingAddress.equals(address)) mAutoConnectingAddress = null;
        if (mBleConnectingAddress != null && mBleConnectingAddress.equals(address)) mBleConnectingAddress = null;
        if (mManualDisconnectAddress != null && mManualDisconnectAddress.equals(address)) mManualDisconnectAddress = null;
        refreshUi();
    }

    private void cancelPairingState(BluetoothDevice device) {
        String address = device.getAddress();
        if ((mPendingPairDevice != null && mPendingPairDevice.getAddress().equals(address))
                || address.equals(mPendingBleConnectAddress)) {
            mPendingPairDevice = null;
            mPendingBleConnectAddress = null;
        }
        mShowBlePairingTip = false;
        if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
            cancelBondProcessCompat(device);
        }
    }

    private void connectHidProfile(BluetoothDevice device, int attempt) {
        if (mAdapter == null) return;
        if (isIgnoredAddress(device.getAddress())) return;
        if (mHidHostProxy == null) {
            if (attempt < 10) {
                mMainHandler.postDelayed(() -> connectHidProfile(device, attempt + 1), 1000);
            }
            return;
        }
        mAdapter.cancelDiscovery();
        int state = BluetoothProfile.STATE_DISCONNECTED;
        try {
            state = mHidHostProxy.getConnectionState(device);
        } catch (Exception ignored) {}
        if (state == BluetoothProfile.STATE_CONNECTED) {
            if (device.getAddress().equals(mBleConnectingAddress)) mBleConnectingAddress = null;
            markConnected(device);
            refreshUi();
            return;
        }
        if (state == BluetoothProfile.STATE_CONNECTING) {
            if (attempt < 10) {
                mMainHandler.postDelayed(() -> connectHidProfile(device, attempt + 1), 1200);
            }
            return;
        }
        boolean ok = false;
        try {
            Method method = mHidHostProxy.getClass().getMethod("connect", BluetoothDevice.class);
            method.setAccessible(true);
            Object result = method.invoke(mHidHostProxy, device);
            ok = result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            Log.e(BLUE_SCREEN_TAG, "hid connect error: " + e.getMessage());
        }
        final boolean finalOk = ok;
        mMainHandler.postDelayed(() -> {
            boolean connected = false;
            if (mHidHostProxy != null) {
                try {
                    connected = mHidHostProxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED;
                } catch (Exception ignored) {}
            }
            if (connected) {
                if (device.getAddress().equals(mBleConnectingAddress)) mBleConnectingAddress = null;
                markConnected(device);
                updatePairedDevices();
                refreshUi();
            } else if (!isIgnoredAddress(device.getAddress()) && attempt < 10) {
                connectHidProfile(device, attempt + 1);
            } else {
                if (device.getAddress().equals(mBleConnectingAddress)) mBleConnectingAddress = null;
                markDisconnected(device);
                updatePairedDevices();
                refreshUi();
            }
        }, finalOk ? 1200 : 1000);
    }

    private void connectHidProfile(BluetoothDevice device) {
        connectHidProfile(device, 0);
    }

    private final BluetoothGattCallback mBleGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            mMainHandler.post(() -> {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (isIgnoredAddress(gatt.getDevice().getAddress())) {
                        try {
                            gatt.disconnect();
                            gatt.close();
                        } catch (Exception ignored) {}
                        if (mActiveGatt != null && mActiveGatt.getDevice().getAddress().equals(gatt.getDevice().getAddress())) {
                            mActiveGatt = null;
                        }
                        markDisconnected(gatt.getDevice());
                        removeDeviceFromUi(gatt.getDevice());
                        return;
                    }
                    if (isLikelyRemote(gatt.getDevice())) {
                        gatt.discoverServices();
                        connectHidProfile(gatt.getDevice());
                        mMainHandler.postDelayed(() -> {
                            if (gatt.getDevice().getAddress().equals(mBleConnectingAddress)) mBleConnectingAddress = null;
                            if (isDeviceConnectedNow(gatt.getDevice())) {
                                markConnected(gatt.getDevice());
                            } else {
                                markDisconnected(gatt.getDevice());
                            }
                            refreshUi();
                        }, 1500);
                    } else {
                        if (gatt.getDevice().getAddress().equals(mBleConnectingAddress)) mBleConnectingAddress = null;
                        mBleConnectedDevice = gatt.getDevice();
                        markConnected(gatt.getDevice());
                        gatt.discoverServices();
                        refreshUi();
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(gatt.getDevice().getAddress())) {
                        mBleConnectedDevice = null;
                    }
                    markDisconnected(gatt.getDevice());
                    gatt.close();
                    if (gatt.getDevice().getAddress().equals(mManualDisconnectAddress)) {
                        mManualDisconnectAddress = null;
                    }
                }
            });
        }
    };

    private void startBondAndConnect(BluetoothDevice device) {
        stopScan();
        mPendingBleConnectAddress = device.getAddress();
        mPendingPairDevice = device;
        mShowBlePairingTip = true;
        boolean started = device.createBond();
        if (!started) {
            Toast.makeText(this, "发起配对失败，请重试", Toast.LENGTH_SHORT).show();
            mPendingBleConnectAddress = null;
            mPendingPairDevice = null;
        } else if (isLikelyRemote(device)) {
            mMainHandler.postDelayed(() -> {
                if (!isDeviceConnectedNow(device)) connectHidProfile(device);
            }, 500);
        }
    }

    private void connectDevice(BluetoothDevice device, boolean clearIgnored, boolean interactive) {
        String address = device.getAddress();
        if (clearIgnored) {
            clearAutoReconnectBlockedAddress(address);
            setConnectionPolicyCompat(device, true);
        }
        if (isIgnoredAddress(address)) {
            if (clearIgnored && device.getBondState() == BluetoothDevice.BOND_NONE) {
                clearIgnoredAddress(address);
            } else {
                Log.i(BLUE_SCREEN_TAG, "skip connect ignored bluetooth device address=" + address);
                disconnectDevice(device);
                removeDeviceFromUi(device);
                return;
            }
        }
        mManualDisconnectAddress = null;
        if (device.getBondState() == BluetoothDevice.BOND_NONE) {
            if (interactive) {
                stopScan();
                mInteractivePairRequest = device;
                showInteractivePairDialog();
                return;
            }
            startBondAndConnect(device);
            return;
        }
        stopScan();
        mBleConnectingAddress = address;
        if (isLikelyRemote(device)) {
            connectHidProfile(device);
            return;
        }
        if (isBleDevice(device)) {
            if (mActiveGatt != null) mActiveGatt.close();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mActiveGatt = device.connectGatt(this, false, mBleGattCallback, BluetoothDevice.TRANSPORT_LE);
            } else {
                mActiveGatt = device.connectGatt(this, false, mBleGattCallback);
            }
        } else {
            mMainHandler.postDelayed(() -> {
                if (address.equals(mBleConnectingAddress)) mBleConnectingAddress = null;
                if (isDeviceConnectedNow(device)) {
                    markConnected(device);
                }
                refreshUi();
            }, 1800);
        }
    }

    private void maybeAutoConnectRemote(BluetoothDevice device) {
        if (mShowBleRemoteDialog) return;
        if (isIgnoredAddress(device.getAddress())) return;
        if (isAutoReconnectBlocked(device.getAddress())) return;
        if (!isTelecomRemote(device)) return;
        if (device.getBondState() != BluetoothDevice.BOND_BONDED) return;
        if (isDeviceConnectedNow(device)) return;
        String address = device.getAddress();
        if (address.equals(mAutoConnectingAddress) || address.equals(mBleConnectingAddress)) return;
        mAutoConnectingAddress = address;
        mMainHandler.post(() -> {
            connectDevice(device, false, false);
            mMainHandler.postDelayed(() -> {
                if (address.equals(mAutoConnectingAddress) && !isDeviceConnectedNow(device)) {
                    mAutoConnectingAddress = null;
                }
            }, 4000);
        });
    }

    private final Map<String, Long> mAutoRemoteAttemptAt = new HashMap<>();

    private void autoConnectAdvertisingRemote(BluetoothDevice device) {
        if (!isTelecomRemote(device)) return;
        String address = device.getAddress();
        boolean forceConnectInPage = mShowBleRemoteDialog;
        if (forceConnectInPage) {
            if (isIgnoredAddress(address)) clearIgnoredAddress(address);
            mIgnoredAtMs.remove(address);
            clearAutoReconnectBlockedAddress(address);
        } else if (isIgnoredAddress(address)) {
            Long ignoredAt = mIgnoredAtMs.get(address);
            boolean inSelfAdvWindow = ignoredAt != null && SystemClock.elapsedRealtime() - ignoredAt < 150_000;
            if (inSelfAdvWindow) return;
            Log.i(BLUE_SCREEN_TAG, "ignored remote advertising again(user re-pairing), clear ignore address=" + address);
            clearIgnoredAddress(address);
        }
        if (device.getBondState() == BluetoothDevice.BOND_BONDING) return;
        if (isDeviceConnectedNow(device)) return;
        if (!forceConnectInPage && (mPendingBleConnectAddress != null || mPendingPairDevice != null)) return;
        if (address.equals(mAutoConnectingAddress) || address.equals(mBleConnectingAddress)) return;
        long now = SystemClock.elapsedRealtime();
        Long last = mAutoRemoteAttemptAt.get(address);
        if (last != null && now - last < 6_000) return;
        mAutoRemoteAttemptAt.put(address, now);
        Log.i(BLUE_SCREEN_TAG, "remote in pairing mode(advertising), auto connect address=" + address + " bond=" + device.getBondState());
        connectDevice(device, true, false);
    }

    private boolean removeBondCompat(BluetoothDevice device) {
        try {
            return device.removeBond();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean cancelBondProcessCompat(BluetoothDevice device) {
        try {
            return device.cancelBondProcess();
        } catch (Exception e) {
            return false;
        }
    }

    private void setConnectionPolicyCompat(BluetoothDevice device, boolean allowed) {
        int policy = allowed ? 100 : 0;
        if (mAdapter == null) return;
        for (int profileId : new int[]{4, BluetoothProfile.A2DP, BluetoothProfile.HEADSET}) {
            mAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    try {
                        Method method = proxy.getClass().getMethod("setConnectionPolicy", BluetoothDevice.class, int.class);
                        method.setAccessible(true);
                        method.invoke(proxy, device, policy);
                    } catch (Exception e1) {
                        try {
                            Method method = proxy.getClass().getMethod("setPriority", BluetoothDevice.class, int.class);
                            method.setAccessible(true);
                            method.invoke(proxy, device, policy);
                        } catch (Exception e2) {
                            Log.d(BLUE_SCREEN_TAG, "set connection policy failed: " + e2.getMessage());
                        }
                    }
                    mMainHandler.postDelayed(() -> mAdapter.closeProfileProxy(profile, proxy), 1000);
                }

                @Override
                public void onServiceDisconnected(int profile) {}
            }, profileId);
        }
    }

    private boolean forgetDevice(BluetoothDevice device) {
        String address = device.getAddress();
        addIgnoredAddress(address);
        cancelPairingState(device);
        if ((mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(address))
                || (mActiveGatt != null && mActiveGatt.getDevice() != null && mActiveGatt.getDevice().getAddress().equals(address))) {
            if (mActiveGatt != null) {
                try {
                    mActiveGatt.disconnect();
                } catch (Exception ignored) {}
                try {
                    mActiveGatt.close();
                } catch (Exception ignored) {}
            }
            mActiveGatt = null;
        }
        int bondState = device.getBondState();
        if (bondState == BluetoothDevice.BOND_BONDING) {
            cancelBondProcessCompat(device);
        }
        boolean started = bondState == BluetoothDevice.BOND_NONE || removeBondCompat(device);
        if (!started) {
            Log.w(BLUE_SCREEN_TAG, "removeBond returned false address=" + address + "; keep ignored");
        }
        markDisconnected(device);
        if (mConnectedDevice != null && mConnectedDevice.getAddress().equals(address)) mConnectedDevice = null;
        if (mBleConnectedDevice != null && mBleConnectedDevice.getAddress().equals(address)) mBleConnectedDevice = null;
        if (mAutoConnectingAddress != null && mAutoConnectingAddress.equals(address)) mAutoConnectingAddress = null;
        if (mBleConnectingAddress != null && mBleConnectingAddress.equals(address)) mBleConnectingAddress = null;
        removeDeviceFromUi(device);
        updatePairedDevices();
        mPendingBleConnectAddress = null;
        mPendingPairDevice = null;
        mDiscoveredClassicDevices.clear();
        mCachedBleDevices.clear();
        mMainHandler.postDelayed(this::updatePairedDevices, 800);
        return started;
    }

    private void addIgnoredAddress(String address) {
        mForgettingAddresses.add(address);
        mIgnoredAtMs.put(address, SystemClock.elapsedRealtime());
        persistIgnoredAddresses();
        Log.i(BLUE_SCREEN_TAG, "ignore bluetooth device address=" + address);
    }

    private void clearIgnoredAddress(String address) {
        if (mForgettingAddresses.remove(address)) {
            persistIgnoredAddresses();
            Log.i(BLUE_SCREEN_TAG, "clear ignored bluetooth device address=" + address);
        }
    }

    private void addAutoReconnectBlockedAddress(String address) {
        mBlockedAutoReconnectAddresses.add(address);
        persistBlockedAutoReconnectAddresses();
        Log.i(BLUE_SCREEN_TAG, "block bluetooth auto reconnect address=" + address);
    }

    private void clearAutoReconnectBlockedAddress(String address) {
        if (mBlockedAutoReconnectAddresses.remove(address)) {
            persistBlockedAutoReconnectAddresses();
            Log.i(BLUE_SCREEN_TAG, "clear bluetooth auto reconnect block address=" + address);
        }
    }

    private boolean isIgnoredAddress(String address) {
        return mForgettingAddresses.contains(address);
    }

    private boolean isAutoReconnectBlocked(String address) {
        return mBlockedAutoReconnectAddresses.contains(address);
    }

    private void showRenameDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_rename, null);
        EditText et = view.findViewById(R.id.et_name);
        et.setText(mBtName);
        builder.setTitle("修改名称").setView(view)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> {
                    String newName = et.getText().toString();
                    if (mAdapter != null && mAdapter.setName(newName)) {
                        mBtName = newName;
                    } else {
                        Toast.makeText(this, "Failed to change name", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void showDeviceOptionsDialog(BluetoothDevice device) {
        mConnectStateTick++;
        boolean isConnected = isDeviceConnectedForUi(device);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(displayDeviceName(device))
                .setMessage(isConnected ? "已连接" : "未连接")
                .setNegativeButton("忽略此设备", (dialog, which) -> {
                    if (!forgetDevice(device)) {
                        Toast.makeText(this, "忽略失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                })
                .setPositiveButton(isConnected ? "取消连接" : "连接设备", (dialog, which) -> {
                    if (isDeviceConnectedForUi(device)) {
                        disconnectDevice(device);
                    } else {
                        connectDevice(device, true, true);
                    }
                }).show();
    }

    private void showPairingDialog() {
        if (mPairingRequest == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String title = "蓝牙配对请求";
        String msg = "与\"" + displayDeviceName(mPairingRequest.device) + "\"配对";
        if (mPairingRequest.needsInput()) {
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null);
            EditText et = view.findViewById(R.id.et_pin);
            builder.setTitle(title).setMessage(msg).setView(view)
                    .setNegativeButton("取消", (dialog, which) -> {
                        try {
                            mPairingRequest.device.setPairingConfirmation(false);
                        } catch (Exception ignored) {}
                        try {
                            mPairingRequest.device.cancelBondProcess();
                        } catch (Exception ignored) {}
                        mPairingRequest = null;
                    })
                    .setPositiveButton("配对", (dialog, which) -> {
                        String pin = et.getText().toString();
                        try {
                            mPairingRequest.device.setPin(pin.getBytes("UTF-8"));
                        } catch (Exception e) {
                            Log.e(BLUE_SCREEN_TAG, "setPin error: " + e.getMessage());
                        }
                        mPairingRequest = null;
                    }).show();
        } else {
            if (mPairingRequest.passkey != null && !mPairingRequest.passkey.isEmpty()) {
                msg += "\n配对码: " + mPairingRequest.passkey;
            }
            builder.setTitle(title).setMessage(msg)
                    .setNegativeButton("取消", (dialog, which) -> {
                        try {
                            mPairingRequest.device.setPairingConfirmation(false);
                        } catch (Exception ignored) {}
                        mPairingRequest = null;
                    })
                    .setPositiveButton("配对", (dialog, which) -> {
                        try {
                            mPairingRequest.device.setPairingConfirmation(true);
                        } catch (Exception ignored) {}
                        mPairingRequest = null;
                    }).show();
        }
    }

    private void showInteractivePairDialog() {
        if (mInteractivePairRequest == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("蓝牙配对请求")
                .setMessage("与\"" + displayDeviceName(mInteractivePairRequest) + "\"配对")
                .setNegativeButton("取消", (dialog, which) -> mInteractivePairRequest = null)
                .setPositiveButton("配对", (dialog, which) -> {
                    BluetoothDevice device = mInteractivePairRequest;
                    mInteractivePairRequest = null;
                    startBondAndConnect(device);
                }).show();
    }

    private void showBleRemoteDialog() {
        mShowBleRemoteDialog = true;
        final Dialog dialog = new Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ble_remote);
        dialog.setCancelable(true);
        dialog.setOnDismissListener(d -> mShowBleRemoteDialog = false);

        ImageView ivBack = dialog.findViewById(R.id.iv_back);
        TextView tvRefresh = dialog.findViewById(R.id.tv_refresh);
        ListView lvRemote = dialog.findViewById(R.id.lv_remote);
        TextView tvTip = dialog.findViewById(R.id.tv_tip);

        mRemoteAdapter = new DeviceAdapter(new ArrayList<>(), false);
        lvRemote.setAdapter(mRemoteAdapter);

        Runnable refreshRemote = new Runnable() {
            @Override
            public void run() {
                List<BluetoothDevice> all = new ArrayList<>();
                all.addAll(mCachedBleDevices);
                all.addAll(mDiscoveredClassicDevices);
                for (BluetoothDevice d : mPairedDevices) {
                    if (isTelecomRemote(d)) all.add(d);
                }
                List<BluetoothDevice> candidates = new ArrayList<>();
                Set<String> added = new HashSet<>();
                for (BluetoothDevice d : all) {
                    if (isTelecomRemote(d) && !added.contains(d.getAddress())) {
                        candidates.add(d);
                        added.add(d.getAddress());
                    }
                }
                String pairingAddress = mPendingBleConnectAddress;
                if (pairingAddress == null) {
                    for (BluetoothDevice d : candidates) {
                        if (d.getBondState() == BluetoothDevice.BOND_BONDING) {
                            pairingAddress = d.getAddress();
                            break;
                        }
                    }
                }
                String connectedAddress = null;
                for (BluetoothDevice d : candidates) {
                    if (!d.getAddress().equals(pairingAddress) && isDeviceConnectedLive(d)) {
                        connectedAddress = d.getAddress();
                        break;
                    }
                }
                mConnectStateTick++;
                String stickyReconnectingAddress = null;
                if (mStickyConnectedRemoteAddress != null
                        && !mStickyConnectedRemoteAddress.equals(connectedAddress)
                        && !mStickyConnectedRemoteAddress.equals(pairingAddress)
                        && mStickyConnectedUntilMs > SystemClock.elapsedRealtime()) {
                    for (BluetoothDevice d : candidates) {
                        if (d.getAddress().equals(mStickyConnectedRemoteAddress)) {
                            stickyReconnectingAddress = mStickyConnectedRemoteAddress;
                            break;
                        }
                    }
                }
                String effectiveConnectingAddress = mBleConnectingAddress != null ? mBleConnectingAddress : stickyReconnectingAddress;
                String finalPairingAddress = pairingAddress;
                String finalConnectedAddress = connectedAddress;
                final String finalStickyReconnectingAddress = stickyReconnectingAddress;
                mMainHandler.post(() -> {
                    mRemoteAdapter.setRemoteMode(true, finalPairingAddress, effectiveConnectingAddress, finalConnectedAddress);
                    mRemoteAdapter.setDevices(candidates);
                    mRemoteAdapter.notifyDataSetChanged();
                    String tipText = null;
                    if (mShowBlePairingTip) tipText = "配对成功，正在发起连接...";
                    else if (mShowBleDisconnectTip && finalConnectedAddress == null && finalStickyReconnectingAddress == null) {
                        tipText = "遥控器已断开连接";
                    }
                    tvTip.setText(tipText != null ? tipText : "");
                    tvTip.setVisibility(tipText != null ? View.VISIBLE : View.GONE);
                });
            }
        };

        ivBack.setOnClickListener(v -> dialog.dismiss());
        tvRefresh.setOnClickListener(v -> startScan());
        lvRemote.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = mRemoteAdapter.getItem(position);
            if (isDeviceConnectedLive(device)) {
                disconnectDevice(device);
            } else {
                connectDevice(device, true, true);
            }
        });

        startScan();
        mMainHandler.post(refreshRemote);
        final Runnable[] periodic = new Runnable[1];
        periodic[0] = () -> {
            if (!mShowBleRemoteDialog || !dialog.isShowing()) return;
            refreshRemote.run();
            mMainHandler.postDelayed(periodic[0], 1000);
        };
        mMainHandler.postDelayed(periodic[0], 1000);

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mBtName = mAdapter.getName();
        }
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
        unregisterReceiverSafe();
        if (mHidHostProxy != null && mAdapter != null) {
            mAdapter.closeProfileProxy(4, mHidHostProxy);
            mHidHostProxy = null;
        }
    }

    private class DeviceAdapter extends BaseAdapter {
        private List<BluetoothDevice> mDevices = new ArrayList<>();
        private boolean mIsPaired;
        private boolean mRemoteMode = false;
        private String mPairingAddress;
        private String mConnectingAddress;
        private String mConnectedAddress;

        DeviceAdapter(List<BluetoothDevice> devices, boolean isPaired) {
            this.mDevices = devices;
            this.mIsPaired = isPaired;
        }

        void setDevices(List<BluetoothDevice> devices) {
            this.mDevices = devices;
        }

        void setRemoteMode(boolean remote, String pairing, String connecting, String connected) {
            mRemoteMode = remote;
            mPairingAddress = pairing;
            mConnectingAddress = connecting;
            mConnectedAddress = connected;
        }

        @Override
        public int getCount() {
            return mDevices.size();
        }

        @Override
        public BluetoothDevice getItem(int position) {
            return mDevices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(BlueScreen.this).inflate(R.layout.item_bluetooth_device, parent, false);
                holder = new ViewHolder();
                holder.tvName = convertView.findViewById(R.id.tv_name);
                holder.tvAddress = convertView.findViewById(R.id.tv_address);
                holder.tvStatus = convertView.findViewById(R.id.tv_status);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            BluetoothDevice device = mDevices.get(position);
            holder.tvName.setText(displayDeviceName(device));
            holder.tvAddress.setText(device.getAddress());
            if (mRemoteMode) {
                String address = device.getAddress();
                String stateText;
                if (address.equals(mPairingAddress)) stateText = "配对中...";
                else if (address.equals(mConnectingAddress)) stateText = "连接中...";
                else if (address.equals(mConnectedAddress)) stateText = "已连接";
                else stateText = "点击连接";
                holder.tvStatus.setText(stateText);
            } else if (mIsPaired) {
                boolean connected = isDeviceConnectedForUi(device);
                holder.tvStatus.setText(connected ? "已连接" : "未连接");
            } else {
                holder.tvStatus.setText("点击连接");
            }
            return convertView;
        }
    }

    private static class ViewHolder {
        TextView tvName;
        TextView tvAddress;
        TextView tvStatus;
    }

    private static class PairingRequestInfo {
        BluetoothDevice device;
        int variant;
        String passkey;

        PairingRequestInfo(BluetoothDevice device, int variant, String passkey) {
            this.device = device;
            this.variant = variant;
            this.passkey = passkey;
        }

        boolean needsInput() {
            return variant == BluetoothDevice.PAIRING_VARIANT_PIN;
        }
    }
}

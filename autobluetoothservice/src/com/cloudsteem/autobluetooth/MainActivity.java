package com.cloudsteem.autobluetooth;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 1001;

    private AutoBlueToothManager mBtManager;
    private ListView mConnectedListView;
    private ListView mScannedListView;
    private DeviceAdapter mConnectedAdapter;
    private DeviceAdapter mScannedAdapter;
    private final List<BluetoothDevice> mConnectedDevices = new ArrayList<>();
    private final List<BluetoothDevice> mScannedDevices = new ArrayList<>();
    private boolean mPermissionReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConnectedListView = findViewById(R.id.lv_connected);
        mScannedListView = findViewById(R.id.lv_scanned);

        mConnectedAdapter = new DeviceAdapter(mConnectedDevices, true);
        mScannedAdapter = new DeviceAdapter(mScannedDevices, false);

        mConnectedListView.setAdapter(mConnectedAdapter);
        mScannedListView.setAdapter(mScannedAdapter);

        mConnectedListView.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = mConnectedDevices.get(position);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("删除配对")
                    .setMessage("确定要删除已连接的遥控器 \"" + device.getName() + "\" 吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        mBtManager.removeBond(device);
                        Toast.makeText(MainActivity.this, "已删除: " + device.getName(), Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        mScannedListView.setOnItemClickListener((parent, view, position, id) -> {
            if (!mPermissionReady) {
                Toast.makeText(MainActivity.this, "请先授予必要的蓝牙权限", Toast.LENGTH_SHORT).show();
                checkAndRequestPermissions();
                return;
            }
            BluetoothDevice device = mScannedDevices.get(position);
            String name = device.getName();
            Toast.makeText(MainActivity.this, "正在连接: " + (name != null ? name : device.getAddress()), Toast.LENGTH_SHORT).show();
            mBtManager.createBond(device);
            mBtManager.connectDevice(device);
        });

        mBtManager = new AutoBlueToothManager(this);
        mBtManager.setDeviceCallback(new AutoBlueToothManager.DeviceCallback() {
            @Override
            public void onScanResult(BluetoothDevice device, int rssi) {
                runOnUiThread(() -> {
                    if (device.getName() == null) {
                        return; // 没有设备名称的不展示
                    }
                    if (mConnectedDevices.contains(device)) {
                        return; // 已连接的不加入扫描列表
                    }
                    if (!mScannedDevices.contains(device)) {
                        mScannedDevices.add(device);
                        mScannedAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onDeviceConnected(BluetoothDevice device) {
                runOnUiThread(() -> {
                    mScannedDevices.remove(device);
                    mScannedAdapter.notifyDataSetChanged();
                    if (!mConnectedDevices.contains(device)) {
                        mConnectedDevices.add(device);
                        mConnectedAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onDeviceDisconnected(BluetoothDevice device) {
                runOnUiThread(() -> {
                    mConnectedDevices.remove(device);
                    mConnectedAdapter.notifyDataSetChanged();
                    if (!mScannedDevices.contains(device) && device.getBondState() != BluetoothDevice.BOND_NONE) {
                        mScannedDevices.add(device);
                        mScannedAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onBondRemoved(BluetoothDevice device) {
                runOnUiThread(() -> {
                    mConnectedDevices.remove(device);
                    mConnectedAdapter.notifyDataSetChanged();
                    mScannedDevices.remove(device);
                    mScannedAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onScanStarted() {
            }

            @Override
            public void onScanFinished() {
            }

            @Override
            public void onServiceReady() {
                runOnUiThread(() -> {
                    // 服务就绪后，先加载已连接设备
                    List<BluetoothDevice> connected = mBtManager.getConnectedDevices();
                    for (BluetoothDevice d : connected) {
                        if (!mConnectedDevices.contains(d)) {
                            mConnectedDevices.add(d);
                        }
                    }
                    mConnectedAdapter.notifyDataSetChanged();
                    // 同时把已配对但未连接的遥控器加入扫描列表
                    List<BluetoothDevice> bonded = mBtManager.getBondedRCDevices();
                    for (BluetoothDevice d : bonded) {
                        if (d.getName() != null
                                && !mConnectedDevices.contains(d)
                                && !mScannedDevices.contains(d)) {
                            mScannedDevices.add(d);
                        }
                    }
                    mScannedAdapter.notifyDataSetChanged();
                });
            }
        });

        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mPermissionReady) {
            return;
        }
        mConnectedDevices.clear();
        mScannedDevices.clear();
        mConnectedAdapter.notifyDataSetChanged();
        mScannedAdapter.notifyDataSetChanged();
        mBtManager.startManualMode();
        mBtManager.startScan();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mBtManager.stopManualMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 确保后台服务继续运行，不在这里 stopService
    }

    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
                permissions.add("android.permission.BLUETOOTH_SCAN");
            }
            if (checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                permissions.add("android.permission.BLUETOOTH_CONNECT");
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < 31) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        } else {
            mPermissionReady = true;
            startBluetoothService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                mPermissionReady = true;
                startBluetoothService();
                if (mBtManager != null) {
                    mConnectedDevices.clear();
                    mScannedDevices.clear();
                    mBtManager.startManualMode();
                    mBtManager.startScan();
                }
            } else {
                Toast.makeText(this, "缺少蓝牙或位置权限，无法扫描遥控器", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startBluetoothService() {
        Intent serviceIntent = new Intent(this, AutoBluetoothService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private class DeviceAdapter extends BaseAdapter {
        private List<BluetoothDevice> mDevices;
        private boolean mIsConnected;

        DeviceAdapter(List<BluetoothDevice> devices, boolean isConnected) {
            this.mDevices = devices;
            this.mIsConnected = isConnected;
        }

        @Override
        public int getCount() {
            return mDevices.size();
        }

        @Override
        public Object getItem(int position) {
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
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_bluetooth_device, parent, false);
                holder = new ViewHolder();
                holder.tvName = convertView.findViewById(R.id.tv_name);
                holder.tvAddress = convertView.findViewById(R.id.tv_address);
                holder.tvStatus = convertView.findViewById(R.id.tv_status);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            BluetoothDevice device = mDevices.get(position);
            holder.tvName.setText(device.getName() != null ? device.getName() : "未知设备");
            holder.tvAddress.setText(device.getAddress());
            if (mIsConnected) {
                holder.tvStatus.setText("已连接 (点击删除)");
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
}

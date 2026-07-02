package com.cloudsteem.autobluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Set;

public class AutoBlueToothManager {

  public Context conext;
  public String TAG = "AutoBlueToothManager";
  private BluetoothAdapter mBtAdapter;
  private BluetoothProfile mService;
  private boolean isBound = false; // 是否正在配对 ， 正在配对则不允许配对
  CountDownTimer mcountDownTimer;
  boolean isscaning = false;

  public AutoBlueToothManager(Context conext) {
    Log.d(TAG, "AutoBlueToothManager 构造函数====");
    this.conext = conext;
    mcountDownTimer = new CountDownTimer(Integer.MAX_VALUE, 3000) {
      @Override
      public void onTick(long millisUntilFinished) {
        Log.d(TAG, "定时器：    是否正在扫描=" + isscaning);
        if (!isscaning)
          enableLeScan(true);
      }

      @Override
      public void onFinish() {}
    };
  }

  public void start() {
    Log.d(TAG, "start...");
    mBtAdapter = BluetoothAdapter.getDefaultAdapter();
    mBtAdapter.getProfileProxy(conext, mListener, 4);
    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothDevice.ACTION_FOUND);
    filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
    filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
    filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
    filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

    conext.registerReceiver(mReceiver, filter);
    if (!mBtAdapter.isEnabled()) {
      mBtAdapter.enable();
    }
    mcountDownTimer.start();
  }

  private boolean hasConnectedDevice() {
    Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
    for (BluetoothDevice device : pairedDevices) {
      if (mService.getConnectionState(device) ==
          BluetoothProfile.STATE_CONNECTED) {
        Log.d(TAG, "has connected");
        return true;
      }
    }
    Log.d(TAG, "no connected");
    return false;
  }

  private boolean hasBondedDevice() {
    Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
    for (BluetoothDevice device : pairedDevices) {
      if (device.getName() != null) {
        if (judgeRC(device)) {
          Log.d(TAG, "hasBondedDevice true");
          unpairDevice(device);
          if (!hasRemoteConnectedDevice()) {
            mBtAdapter.disable();
            new Handler().postDelayed(() -> mBtAdapter.enable(), 800);
          }
          return true;
        }
      }
    }
    Log.d(TAG, "hasBondedDevice false");
    return false;
  }

  /*
      private boolean hasBondedDevice() {
          Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
          for (BluetoothDevice device : pairedDevices) {
              if (device.getName() != null && judgeRC(device)) {
                  Log.d(TAG, "发现已配对的遥控器: " + device.getName());
                  return true;
              }
          }
          Log.d(TAG, "没有已配对的遥控器");
          return false;
      }
  */

  private void onDeviceFound(BluetoothDevice bluetoothDevice, int rssi) {
    if (judgeRC(bluetoothDevice)) {
      BluetoothDevice bd = bluetoothDevice;
      Log.d(TAG, "###发现遥控器####   name=" + bd.getName() +
                     "          getBondState = " + bd.getBondState() +
                     "信号强度：" + rssi);
      //            if (rssi > 0 && rssi < 50) {
      // 满足一定的强度
      enableLeScan(false);
      if (bd.getBondState() == BluetoothDevice.BOND_NONE) { // 10
        if (hasBondedDevice()) {
          Log.d(TAG, ">>>>>>>>>>>hasBondedDevice    "
                         + "存在已配对的遥控器，并进行删除操作");
          //                    return;
        }
        Log.d(TAG, "start to createBond......");
        creatbond(bd);

        Log.d(TAG, "start to connect......");
        new Handler().postDelayed(() -> {
          Log.d(TAG, "mService: " + mService +
                         ",mService.getConnectionState: " +
                         mService.getConnectionState(bd));
          if (mService != null && mService.getConnectionState(bd) ==
                                      BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(TAG, "尝试连接设备: " + bd.getName());
            connect(bd);
          }
        }, 500);
      } else if (bd.getBondState() == BluetoothDevice.BOND_BONDING) { // 11
        isBound = false;
      }
      //            else if (bd.getBondState() == BluetoothDevice.BOND_BONDED)
      //            {//12
      //                if (hasConnectedDevice()) {
      //                    return;
      //                }
      //                connect(bd);
      //            }
      //            }
    }
  }

  @SuppressLint("NewApi")
  private BluetoothProfile.ServiceListener mListener =
      new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
          try {
            if (profile == 4) {
              mService = proxy;
              Log.d(TAG, "###BluetoothProfile 服务已连接: " + mService);
              // mService = (BluetoothHidHost) proxy;
              // Log.d(TAG, "BluetoothProfile 服务已连接: " + mService);
            }

          } catch (Exception e) {
            e.printStackTrace();
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
      Log.d(TAG, "#####[广播]" + intent.getAction());
      String action = intent.getAction();
      if (BluetoothDevice.ACTION_FOUND.equals(action)) {

        BluetoothDevice bluetoothDevice =
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        Log.d(TAG, "bluetoothDevice name: " + bluetoothDevice.getName());
        int rssi = intent.getExtras().getShort(
            BluetoothDevice.EXTRA_RSSI); // 获取额外rssi值
        onDeviceFound(bluetoothDevice, Math.abs(rssi));
      } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
        /**配对开始时，配对成功时**/
        BluetoothDevice bd =
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        Log.d(TAG, "#####配对开始时#####BtAutoPair BroadcastReceiver state: " +
                       bd.getBondState());
        switch (bd.getBondState()) {
        case BluetoothDevice.BOND_BONDED:
          isBound = false;
          Log.d(TAG, "配对成功#####");

          switch (mService.getConnectionState(bd)) {
          case BluetoothProfile.STATE_DISCONNECTED:
            //                                Log.d(TAG,
            //                                "配置文件处于断开连接状态====>>>>执行mService.connect");
            Log.d(TAG, "配置文件处于断开连接状态====>>>>");
            //                                if (judgeRC(bd)) {
            //                                    sound.play();
            //                                }
            break;
          case BluetoothProfile.STATE_CONNECTING:
            Log.d(TAG, "配置文件处于连接ing状态====>>>>");
            break;
          case BluetoothProfile.STATE_CONNECTED:
            Log.d(TAG, "配置文件处于连接状态====>>>>");
            break;
          case BluetoothProfile.STATE_DISCONNECTING:
            Log.d(TAG, "配置文件处于断开连接ing状态====>>>>");
            break;
          }
          break;
        case BluetoothDevice.BOND_NONE:
          connect(bd);
          isBound = false;
          Log.d(TAG, "配对失败#####");
          break;
        case BluetoothDevice.BOND_BONDING:
          isBound = true;
          Log.d(TAG, "配对ing#####");
          break;
        }
      } else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
        /**当蓝牙的状态发生改变时，会出现这个广播**/
        final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                                             BluetoothAdapter.ERROR);
        Log.d(TAG,
              "#####当蓝牙的状态发生改变时，会出现这个广播#####"
                  +
                  "BtAutoPair BroadcastReceiver ACTION_STATE_CHANGED state =" +
                  state);
        switch (state) {
        case BluetoothAdapter.STATE_ON:
          enableLeScan(true);
          break;
        case BluetoothAdapter.STATE_OFF:
          //                        mBtAdapter.enable();
          break;
        }
      } else if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
        //                BluetoothDevice bd =
        //                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        /**配对结束时，断开连接**/
        Log.d(TAG, "[拔电池？？？]####配对结束时，断开连接######BtAutoPair "
                       + "BroadcastReceiver ACTION_ACL_DISCONNECTED");
      } else if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
        isBound = true;
        /**发起连接**/
        BluetoothDevice bd =
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String name = bd.getName();
        Log.d(TAG, "[回连？？？]#####发起连接#####BtAutoPair "
                       + "BroadcastReceiver ACTION_ACL_CONNECTED    " + name);
      } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
        isscaning = true;
        /**开始搜索**/
        Log.d(TAG, "######开始搜索####");
        //                enableLeScan(true);
      } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
        isscaning = false;
        /**搜索结束。重新搜索时**/
        Log.d(TAG, "######搜索结束####");
        //                getPairRC();
        //                new Handler().postDelayed(() -> enableLeScan(true),
        //                2000);
      }
    }
  };

  public void enableLeScan(boolean enable) {
    Log.d(TAG, "##########enableLeScan: " + enable);
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

  /*
  public void connect(BluetoothDevice device) {
      Log.i(TAG, "connectKeyboard");
      if (mService == null) {
          return;
      }
      try {
          Method method = mService.getClass().getMethod("connect",
                  new Class[]{BluetoothDevice.class});
          method.invoke(mService, device);
      } catch (Exception e) {
          e.printStackTrace();
      }
  }
   */

  public void connect(BluetoothDevice device) {
    Log.i(TAG, "尝试连接设备: " + device.getName());
    mBtAdapter.cancelDiscovery();
    if (mService == null) {
      Log.e(TAG, "mService 未初始化，无法连接");
      return;
    }
    try {
      // 检查是否已连接
      if (mService.getConnectionState(device) ==
          BluetoothProfile.STATE_CONNECTED) {
        Log.d(TAG, "设备已连接，无需重复连接");
        return;
      }
      // 使用反射调用 connect()
      Method method =
          mService.getClass().getMethod("connect", BluetoothDevice.class);
      boolean success = (boolean)method.invoke(mService, device);
      // mService.connect(device);
      Log.d(TAG, "连接结果: " + (success ? "成功" : "失败"));
      //            if (success) mcountDownTimer.cancel();
      // 如果失败，延迟 1s 后重试
      if (!success) {
        new Handler().postDelayed(() -> connect(device), 1000);
      }
    } catch (Exception e) {
      Log.e(TAG, "连接异常: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public void creatbond(BluetoothDevice device) {
    if (!isBound) {
      try {
        boolean result = createBond(device.getClass(), device);
        Log.d(TAG, "[createBond] result: " + result);
      } catch (Exception e) {
        e.printStackTrace();
        Log.i(TAG, "[creatbond]配对异常" + e.getMessage());
      }
    }
  }

  public boolean createBond(Class btClass, BluetoothDevice btDevice)
      throws Exception {
    Method createBondMethod = btClass.getMethod("createBond");
    Boolean returnValue = (Boolean)createBondMethod.invoke(btDevice);
    return returnValue.booleanValue();
  }

  public void unpairDevice(BluetoothDevice device) {
    Log.e(TAG, "[unpairDevice]删除配对设备!");
    try {
      Method m = device.getClass().getMethod("removeBond", (Class[])null);
      m.invoke(device, (Object[])null);
    } catch (Exception e) {
      Log.e(TAG, e.getMessage());
    }
  }

  public boolean judgeRC(BluetoothDevice device) {
    if (device.getName() == null)
      return false;
    return device.getName().contains("RC-01") ||
        device.getName().contains("RC-03") ||
        device.getName().contains("电信蓝牙遥控");
  }

  private boolean getPairRC() {
    try {
      Set<BluetoothDevice> devices = mBtAdapter.getBondedDevices();
      for (BluetoothDevice device : devices) {
        if (device != null) {
          if (judgeRC(device)) {
            Log.e(TAG, "存在匹配的遥控器   " + device.getName() +
                           "   state:" + device.getBondState());
            return true;
          }
        }
      }
      Log.e(TAG, "不存在匹配的遥控器");
      return false;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }

  // 是否有指定遥控器连接着
  public boolean hasRemoteConnectedDevice() {
    Log.d(TAG, "Fun() hasConnectedDevice");
    Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

    Log.d(TAG, "pairedDevices: " + pairedDevices);
    //        Log.d(TAG, "Devices: "+Devices);
    for (BluetoothDevice device : pairedDevices) {
      Log.d(TAG, "device: " + device);
      if (mService != null)
        if (mService.getConnectionState(device) ==
            BluetoothProfile.STATE_CONNECTED) {
          Log.d(TAG, "has connected");
          if (judgeRC(device)) {
            return true;
          } else {
            return false;
          }
        }
    }
    Log.d(TAG, "no connected");
    return false;
  }
}

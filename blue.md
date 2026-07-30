# 蓝牙遥控器修复记录

## 目标

电信蓝牙遥控器的配对、连接、扫描统一交给系统内置 AutoBluetooth 处理，SettingsC 不再实现自己的遥控器连接器，避免两个模块同时连接同一个遥控器导致冲突。

SettingsC 只做：

- 展示蓝牙 UI。
- 读取已配对状态。
- 读取 HID/ACL 连接状态。
- 遥控器页可主动扫描刷新列表，但扫描结果只用于展示状态。
- 被动接收 AutoBluetooth 扫描/连接过程中发出的系统蓝牙广播并刷新 UI。

## 已改动

- `BlueScreen.kt`
  - 电信遥控器命中 `RC-01`、`RC-03`、`电信蓝牙遥控` 后，直接跳过本地 `createBond()`、HID `connect()`、`connectGatt()`。
  - 不再抢 `ACTION_PAIRING_REQUEST`，配对确认交给系统/AutoBluetooth。
  - “蓝牙遥控器”页进入和点击刷新时会扫描发现遥控器列表，但不主动配对/连接电信遥控器。
  - 恢复“遥控器列表”显示，避免扫描到了但页面没有展示区域。
  - 普通蓝牙设备的手动连接、配对逻辑保留。

- `BluetoothRemoteAutoConnector.kt`
  - 降级为状态观察器。
  - 即使以后被启动，也只监听 `ACTION_FOUND`、`ACTION_BOND_STATE_CHANGED`、`ACTION_ACL_CONNECTED`、`ACTION_ACL_DISCONNECTED`。
  - 不再 `startDiscovery()`、`createBond()`、HID `connect()`。

- `TvSettingsApplication.kt`
  - 不启动本地 `BluetoothRemoteAutoConnector`。
  - 注释明确：电信遥控器配对/连接由 AutoBluetooth 处理。

## 构建验证

已完成：

```bash
./gradlew :app:assembleDebug
```

结果：`BUILD SUCCESSFUL`。

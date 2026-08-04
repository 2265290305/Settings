package com.cloudsteem.autobluetooth;

/**
 * Read-only status contract consumed by SettingsC.
 *
 * AutoBluetoothService owns all pairing and connection operations. Consumers
 * receive this signature-protected broadcast only to render remote state.
 */
public final class AutoBluetoothStatusContract {
    public static final String ACTION_STATUS_CHANGED =
            "com.cloudsteem.autobluetooth.action.STATUS_CHANGED";
    public static final String ACTION_DISCONNECT_REMOTE =
            "com.cloudsteem.autobluetooth.action.DISCONNECT_REMOTE";
    public static final String ACTION_FORGET_REMOTE =
            "com.cloudsteem.autobluetooth.action.FORGET_REMOTE";
    public static final String PERMISSION_STATUS =
            "com.cloudsteem.autobluetooth.permission.STATUS";
    public static final String EXTRA_CONNECTED = "connected";
    public static final String EXTRA_CONNECTING = "connecting";
    public static final String EXTRA_BOND_STATE = "bond_state";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_NAME = "name";

    private AutoBluetoothStatusContract() {
    }
}

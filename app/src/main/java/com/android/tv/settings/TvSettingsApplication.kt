package com.android.tv.settings

import android.app.Application
import android.util.Log
import com.telecom.quickdetector.QuickDetectorSdk

class TvSettingsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 一键检测 SDK 初始化放后台线程，避免阻塞冷启动（用户进入一键检测页前有充足时间完成）。
        // false=生产环境。
        Thread({
            runCatching {
                QuickDetectorSdk.init(this, false)
            }.onFailure {
                Log.e("TvSettingsApplication", "QuickDetectorSdk init failed", it)
            }
        }, "quickdetector-init").start()

        // 电信遥控器配对/连接由系统 AutoBluetooth 处理；本 app 不启动本地自动连接器。
        runCatching {
            //BluetoothRemoteAutoConnector.start(this)
            AutoBluetoothKeeperService.start(this)
        }.onFailure {
            Log.e("TvSettingsApplication", "AutoBluetooth keeper start failed", it)
        }
    }
}

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

        // 进程级常驻蓝牙遥控器自动配对/回连：persistent 系统 app 开机即拉进程，
        // 不依赖打开设置页即可配对/连接“电信蓝牙遥控”。注册广播需在主线程。
        runCatching {
            //BluetoothRemoteAutoConnector.start(this)
        }.onFailure {
            Log.e("TvSettingsApplication", "BluetoothRemoteAutoConnector start failed", it)
        }
    }
}

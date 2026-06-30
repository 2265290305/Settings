package com.android.tv.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.telecom.tmc.IUpgradeAidlInterface
import com.telecom.tmc.IUpgradeCallback

/**
 * 系统升级（OTA）AIDL 对接客户端。
 *
 * 绑定 TMC（com.tatv.android.TMC）的升级服务，调用 [IUpgradeAidlInterface]：
 *  - checkUpgrade(jsonStr, callback)：触发检查升级，结果通过 [IUpgradeCallback.onResult] 回调；
 *  - getRomUpdateInfo()：获取当前 ROM 升级信息。
 *
 * 绑定 Intent 的 action 未在 AIDL 中给出，这里按常见约定优先用接口全限定名，
 * 再回退到既有的 ROM 升级 action（见 [launchRomUpgradeOrFallback]）。若实际服务 action
 * 不同，只需调整 [BIND_ACTIONS] 即可。回调统一切回主线程，便于直接更新 UI / Toast。
 */
object RomUpgradeClient {
    private const val TAG = "RomUpgradeClient"
    private const val TMC_PACKAGE = "com.tatv.android.TMC"

    // 已实测：TMC 的 ROM 升级服务 = com.telecom.tmc.romupgrade.RomUpgradeService，action 为 com.telecom.romupgrade。
    private val BIND_ACTIONS = listOf(
        "com.telecom.romupgrade",
        "com.telecom.tmc.IUpgradeAidlInterface"
    )

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 触发检查升级。[onResult] 在主线程回调（code 由 TMC 升级服务定义）。
     * @return 是否成功发起绑定（false 表示未找到升级服务，调用方可回退到 intent 方式）。
     */
    fun checkUpgrade(context: Context, jsonStr: String = "{}", onResult: (Int) -> Unit): Boolean {
        return bind(context) { api, unbind ->
            val callback = object : IUpgradeCallback.Stub() {
                override fun onResult(code: Int) {
                    Log.i(TAG, "checkUpgrade onResult code=$code")
                    mainHandler.post { onResult(code) }
                    unbind()
                }
            }
            api.checkUpgrade(jsonStr, callback)
        }
    }

    /**
     * 获取 ROM 升级信息。[onInfo] 在主线程回调。
     * @return 是否成功发起绑定。
     */
    fun getRomUpdateInfo(context: Context, onInfo: (String?) -> Unit): Boolean {
        return bind(context) { api, unbind ->
            val info = runCatching { api.romUpdateInfo }.getOrNull()
            mainHandler.post { onInfo(info) }
            unbind()
        }
    }

    /**
     * 绑定升级服务并在连接成功后回调 [onConnected]，传入接口与 unbind 动作（须由调用方在用完后执行）。
     */
    private fun bind(
        context: Context,
        onConnected: (api: IUpgradeAidlInterface, unbind: () -> Unit) -> Unit
    ): Boolean {
        val appContext = context.applicationContext
        for (action in BIND_ACTIONS) {
            val intent = Intent(action).setPackage(TMC_PACKAGE)
            if (appContext.packageManager.resolveService(intent, 0) == null) continue

            val connection = object : ServiceConnection {
                private var released = false
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val api = IUpgradeAidlInterface.Stub.asInterface(service)
                    val unbind = {
                        if (!released) {
                            released = true
                            runCatching { appContext.unbindService(this) }
                        }
                    }
                    if (api == null) {
                        unbind()
                        return
                    }
                    runCatching { onConnected(api, unbind) }
                        .onFailure {
                            Log.e(TAG, "invoke upgrade api failed", it)
                            unbind()
                        }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    released = true
                }
            }

            val bound = runCatching {
                appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }.getOrDefault(false)
            if (bound) {
                Log.i(TAG, "bind upgrade service via action=$action")
                return true
            }
            runCatching { appContext.unbindService(connection) }
        }
        Log.w(TAG, "no upgrade service resolved for $BIND_ACTIONS @ $TMC_PACKAGE")
        return false
    }
}

package com.android.tv.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle

// 系统进程应用（sharedUserId=android.uid.system）直接 sendBroadcast 不指定 user 时，
// 广播默认只投递到 system user，跑在 user 0 的 Iot Apk 等可能收不到，并打印
// "Calling a method in the system process without a qualified user" 警告。
// 用 sendBroadcastAsUser(UserHandle.ALL) 跨用户投递。UserHandle.ALL 为 @hide 常量，反射获取。
private val USER_HANDLE_ALL: UserHandle? by lazy {
    runCatching { UserHandle::class.java.getField("ALL").get(null) as UserHandle }.getOrNull()
}

/** 跨用户发送广播；取不到 UserHandle.ALL 或调用失败时回退到普通 sendBroadcast。 */
fun Context.sendBroadcastAllUsers(intent: Intent) {
    val all = USER_HANDLE_ALL
    val sent = all != null && runCatching { sendBroadcastAsUser(intent, all) }.isSuccess
    if (!sent) sendBroadcast(intent)
}

const val ACTION_IOT_PAGE_NET_OPTION = "com.android.ctcc.iotctl.action.page.net_option"
const val ACTION_IOT_PAGE_PRIVATE = "com.android.ctcc.iotctl.action.page.private"
private const val ACTION_PULL_PRIVACY = "com.ctcc.action.pull_privacy"
private const val ACTION_ROM_UPGRADE = "com.telecom.romupgrade"
private const val ACTION_REPORT_LOG_MIDDLE = "android.intent.action.reportLogMiddle"
private const val ACTION_SYS_RESET = "com.ctcc.devops.action.sys.reset"
private const val ACTION_FACTORY_RESET = "android.intent.action.FACTORY_RESET"
private const val ACTION_MASTER_CLEAR = "android.intent.action.MASTER_CLEAR"
private const val EXTRA_REASON = "android.intent.extra.REASON"
private const val TMC_PACKAGE = "com.tatv.android.TMC"
private const val ACCLOUD_ACCOUNT_PACKAGE = "com.chinatelecom.accloudbox"
private const val ACCLOUD_PROTOCOL_ACTIVITY = "cn.com.chinatelecom.account.tv.activity.ZpProtocolActivity"
private const val ACCLOUD_PROTOCOL_DETAIL_ACTIVITY = "cn.com.chinatelecom.account.tv.activity.ZpProtocolDetailActivity"
private const val ACCLOUD_SET_PROFILE_ACTIVITY = "cn.com.chinatelecom.account.tv.activity.SetProfileActivity"
private const val SMARTCLOUD_LAUNCHER_PACKAGE = "cn.dlife.smartcloud.launcher"
private const val SMARTCLOUD_LAUNCHER_CLASS = "cn.dlife.smartcloud.launcher.MainActivityZte"

// 天翼智屏用户协议 / 隐私政策的 H5 地址与标题（取自统一账号 APK CtAccountAPP 内置常量），
// 由 ZpProtocolDetailActivity 通过 ProtocolTitle/ProtocolUrl extra 加载展示。
const val PROTOCOL_TITLE_USER_AGREEMENT = "天翼智屏用户协议"
const val PROTOCOL_URL_USER_AGREEMENT =
    "https://hioth5.189smarthome.com/smarthome_h5/speakerUserAgreement/#/"
const val PROTOCOL_TITLE_PRIVACY = "天翼智屏隐私政策"
const val PROTOCOL_URL_PRIVACY =
    "https://hioth5.189smarthome.com/smarthome_h5/speakerPrivacyAgreement/#/"

private val UNIFIED_ACCOUNT_PACKAGES = listOf(
    "cn.com.chinatelecom.account.android",
    "cn.com.chinatelecom.esurfing.account",
)

private val UNIFIED_ACCOUNT_AGREEMENT_ACTIONS = listOf(
    "cn.com.chinatelecom.account.android.action.USER_AGREEMENT",
    "cn.com.chinatelecom.account.action.USER_AGREEMENT",
    "com.chinatelecom.account.action.USER_AGREEMENT",
)

fun Context.openIotPrivatePage() {
    startActivity(Intent(this, MainActivity::class.java).apply {
        action = ACTION_IOT_PAGE_PRIVATE
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    })
}

fun Context.launchSettingsExitTarget() {
    val explicitLauncher = Intent().apply {
        setClassName(SMARTCLOUD_LAUNCHER_PACKAGE, SMARTCLOUD_LAUNCHER_CLASS)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    if (explicitLauncher.canResolve(packageManager)) {
        startActivity(explicitLauncher)
        return
    }

    packageManager.getLaunchIntentForPackage(SMARTCLOUD_LAUNCHER_PACKAGE)?.let { launchIntent ->
        startActivity(launchIntent.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        return
    }

    startActivity(Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    })
}

fun Context.launchUnifiedAccountAgreementOrFallback(): Boolean {
    val explicitProtocol = Intent().apply {
        component = ComponentName(ACCLOUD_ACCOUNT_PACKAGE, ACCLOUD_PROTOCOL_ACTIVITY)
        putExtra("ShowDefaultProtocol", true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (explicitProtocol.canResolve(packageManager)) {
        startActivity(explicitProtocol)
        return true
    }

    val packageManager = packageManager

    for (targetPackage in UNIFIED_ACCOUNT_PACKAGES) {
        for (targetAction in UNIFIED_ACCOUNT_AGREEMENT_ACTIONS) {
            val intent = Intent(targetAction).apply {
                `package` = targetPackage
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.canResolve(packageManager)) {
                startActivity(intent)
                return true
            }
        }

        packageManager.getLaunchIntentForPackage(targetPackage)?.let { launchIntent ->
            startActivity(launchIntent.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return true
        }
    }

    openIotPrivatePage()
    return false
}

fun Context.launchUnifiedAccountProtocolDetailOrFallback(title: String, url: String): Boolean {
    if (url.isBlank()) {
        return launchUnifiedAccountAgreementOrFallback()
    }
    val explicitDetail = Intent().apply {
        component = ComponentName(ACCLOUD_ACCOUNT_PACKAGE, ACCLOUD_PROTOCOL_DETAIL_ACTIVITY)
        putExtra("ProtocolTitle", title)
        putExtra("ProtocolUrl", url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // ZpProtocolDetailActivity 无 intent-filter 且受 com.dlife.permission.ACCESS_ACTIVITY 权限保护，
    // resolveActivity 可能返回 null，不能用 canResolve 预判（否则必走 fallback 跳到 IOTSDK 确认页）。
    // 本应用已声明该权限，直接尝试启动；失败再回退。
    if (runCatching { startActivity(explicitDetail) }.isSuccess) {
        return true
    }
    return launchUnifiedAccountAgreementOrFallback()
}

fun Context.openUnifiedAccountProfileSettings(): Boolean {
    val intent = Intent().apply {
        component = ComponentName(ACCLOUD_ACCOUNT_PACKAGE, ACCLOUD_SET_PROFILE_ACTIVITY)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (!intent.canResolve(packageManager)) {
        return false
    }
    startActivity(intent)
    return true
}

fun Context.launchPrivacyPolicyOrFallback(): Boolean {
    var delivered = false

    UNIFIED_ACCOUNT_PACKAGES
        .filter { packageExists(it) }
        .forEach { pkg ->
            runCatching {
                sendBroadcastAllUsers(Intent(ACTION_PULL_PRIVACY).setPackage(pkg))
                delivered = true
            }
        }

    if (!delivered) {
        runCatching {
            sendBroadcastAllUsers(Intent(ACTION_PULL_PRIVACY))
            delivered = true
        }
    }

    if (delivered) {
        return true
    }
    return launchUnifiedAccountAgreementOrFallback()
}

fun Context.launchRomUpgradeOrFallback(): Boolean {
    val upgradeActivity = Intent(ACTION_ROM_UPGRADE).apply {
        `package` = TMC_PACKAGE
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (upgradeActivity.canResolve(packageManager)) {
        startActivity(upgradeActivity)
        return true
    }

    val startedService = runCatching {
        startService(Intent(ACTION_ROM_UPGRADE).setPackage(TMC_PACKAGE))
    }.getOrNull()
    if (startedService != null) {
        return true
    }

    packageManager.getLaunchIntentForPackage(TMC_PACKAGE)?.let { launchIntent ->
        startActivity(launchIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return true
    }

    return false
}

fun Context.requestLogUpload(): Boolean {
    return runCatching {
        sendOrderedBroadcast(Intent(ACTION_REPORT_LOG_MIDDLE).apply {
            putExtra("reportlog", "1")
        }, null)
        true
    }.getOrDefault(false)
}

/**
 * 打开“恢复出厂设置”入口（仅打开确认界面，不直接擦除）。
 * 优先走定制机的 devops 重置 Activity；该机若无此 Activity 则返回 false，
 * 由调用方弹出自带的确认弹窗，确认后再调用 [performFactoryReset] 真正执行。
 */
fun Context.openFactoryResetEntry(): Boolean {
    val resetIntent = Intent(ACTION_SYS_RESET).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (resetIntent.canResolve(packageManager)) {
        return runCatching {
            startActivity(resetIntent)
            true
        }.getOrDefault(false)
    }
    return false
}

/**
 * 真正触发系统恢复出厂：发送 Android 标准的 FACTORY_RESET / MASTER_CLEAR 广播给
 * 系统的 MasterClearReceiver（packageName=android）。需要 android.permission.MASTER_CLEAR
 * 权限（特权系统 app 已声明）。返回是否成功发出广播。
 */
fun Context.performFactoryReset(reason: String = "TvSettings factory reset"): Boolean {
    // 先尝试定制机 devops 广播（部分机型由其执行擦除）。
    runCatching { sendBroadcast(Intent(ACTION_SYS_RESET)) }

    val sent = runCatching {
        sendBroadcast(Intent(ACTION_FACTORY_RESET).apply {
            setPackage("android")
            putExtra(EXTRA_REASON, reason)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
        true
    }.getOrDefault(false)

    // 兼容旧系统：再补发 MASTER_CLEAR。
    runCatching {
        sendBroadcast(Intent(ACTION_MASTER_CLEAR).apply {
            setPackage("android")
            putExtra(EXTRA_REASON, reason)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        })
    }

    return sent
}

private fun Intent.canResolve(packageManager: PackageManager): Boolean {
    return resolveActivity(packageManager) != null
}

private fun Context.packageExists(packageName: String): Boolean {
    return runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)
}

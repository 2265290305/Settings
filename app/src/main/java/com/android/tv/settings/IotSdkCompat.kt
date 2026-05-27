package com.android.tv.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

const val ACTION_IOT_PAGE_NET_OPTION = "com.android.ctcc.iotctl.action.page.net_option"
const val ACTION_IOT_PAGE_PRIVATE = "com.android.ctcc.iotctl.action.page.private"
private const val ACTION_PULL_PRIVACY = "com.ctcc.action.pull_privacy"
private const val ACTION_ROM_UPGRADE = "com.telecom.romupgrade"
private const val ACTION_REPORT_LOG_MIDDLE = "android.intent.action.reportLogMiddle"
private const val ACTION_SYS_RESET = "com.ctcc.devops.action.sys.reset"
private const val TMC_PACKAGE = "com.tatv.android.TMC"
private const val ACCLOUD_ACCOUNT_PACKAGE = "com.chinatelecom.accloudbox"
private const val ACCLOUD_PROTOCOL_ACTIVITY = "cn.com.chinatelecom.account.tv.activity.ZpProtocolActivity"
private const val ACCLOUD_PROTOCOL_DETAIL_ACTIVITY = "cn.com.chinatelecom.account.tv.activity.ZpProtocolDetailActivity"
private const val ACCLOUD_SET_PROFILE_ACTIVITY = "cn.com.chinatelecom.account.tv.activity.SetProfileActivity"
private const val SMARTCLOUD_LAUNCHER_PACKAGE = "cn.dlife.smartcloud.launcher"
private const val SMARTCLOUD_LAUNCHER_CLASS = "cn.dlife.smartcloud.launcher.MainActivityZte"

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
    if (explicitDetail.canResolve(packageManager)) {
        startActivity(explicitDetail)
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
                sendBroadcast(Intent(ACTION_PULL_PRIVACY).setPackage(pkg))
                delivered = true
            }
        }

    if (!delivered) {
        runCatching {
            sendBroadcast(Intent(ACTION_PULL_PRIVACY))
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

fun Context.openFactoryResetEntry(): Boolean {
    val resetIntent = Intent(ACTION_SYS_RESET).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (resetIntent.canResolve(packageManager)) {
        startActivity(resetIntent)
        return true
    }

    return runCatching {
        sendBroadcast(Intent(ACTION_SYS_RESET))
        true
    }.getOrDefault(false)
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

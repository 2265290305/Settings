package com.android.tv.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import com.telecom.quickdetector.QuickDetectorSdk
import com.telecom.quickdetector.http.model.BusinessPackageInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.roundToInt

private const val ONE_KEY_TAG = "OneKeyCheck"

private data class TcpProbeTarget(val host: String, val port: Int)

// 业务套餐 checkType(1~9) 到 名称/图标 的映射（图标用本地 drawable，名称优先用 SDK 返回的 name）。
private fun businessMeta(checkType: Long): Pair<String, Int>? = when (checkType) {
    1L -> "小翼管家" to R.drawable.account
    2L -> "翼家智话" to R.drawable.ic_info
    3L -> "云回看" to R.drawable.refresh
    4L -> "天翼超高清" to R.drawable.ic_visibility
    5L -> "AI守护" to R.drawable.lock
    6L -> "时光缩影" to R.drawable.path
    7L -> "画面异常巡检" to R.drawable.ic_info
    8L -> "翼家健康" to R.drawable.ic_visibility
    9L -> "天翼云盘" to R.drawable.qrcode
    else -> null
}

private fun businessToServiceItem(bp: BusinessPackageInfo): ServiceItem? {
    val (defName, icon) = businessMeta(bp.checkType) ?: return null
    val title = bp.name?.takeIf { it.isNotBlank() } ?: defName
    // status: 1已绑定 2未绑定 3已开通 4未开通 -> 1/3 视为已绑定/已开通
    val bound = bp.status == "1" || bp.status == "3"
    return ServiceItem(title, bound, icon)
}

private val FALLBACK_SERVICES = listOf(
    ServiceItem("小翼管家", false, R.drawable.account),
    ServiceItem("翼家智话", false, R.drawable.ic_info),
    ServiceItem("云回看", false, R.drawable.refresh),
    ServiceItem("天翼超高清", false, R.drawable.ic_visibility),
    ServiceItem("AI守护", false, R.drawable.lock),
    ServiceItem("时光缩影", false, R.drawable.path),
    ServiceItem("画面异常巡检", false, R.drawable.ic_info),
    ServiceItem("翼家健康", false, R.drawable.ic_visibility),
    ServiceItem("天翼云盘", false, R.drawable.qrcode)
)

private fun bestEffortSsid(context: Context): String? {
    val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    val info = runCatching { wm.connectionInfo }.getOrNull() ?: return null
    val raw = info.ssid?.trim()
    if (raw.isNullOrBlank()) return null
    // Avoid depending on WifiManager.UNKNOWN_SSID (not present in some SDK stubs).
    if (raw.equals("<unknown ssid>", ignoreCase = true)) return null
    return raw.trim('"')
}

private fun bestEffortRssi(context: Context): Int? {
    val wm = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
    val info = runCatching { wm.connectionInfo }.getOrNull() ?: return null
    val v = info.rssi
    // Avoid depending on WifiInfo.INVALID_RSSI (not present in some SDK stubs).
    // AOSP uses -127 as the sentinel for an invalid RSSI.
    return if (v == -127) null else v
}

private fun bestEffortConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun qualityLabelForRssi(rssi: Int?): String {
    if (rssi == null) return "未知"
    return when {
        rssi >= -55 -> "良好"
        rssi >= -67 -> "良好"
        rssi >= -75 -> "一般"
        else -> "较差"
    }
}

private fun qualityLabelForLoss(lossPercent: Int?): String {
    if (lossPercent == null) return "未知"
    return when {
        lossPercent <= 2 -> "良好"
        lossPercent <= 10 -> "一般"
        else -> "较差"
    }
}

private suspend fun tcpConnectLatencyMs(target: TcpProbeTarget, timeoutMs: Int): Long? = withContext(Dispatchers.IO) {
    runCatching {
        val start = System.nanoTime()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(target.host, target.port), timeoutMs)
        }
        val end = System.nanoTime()
        ((end - start) / 1_000_000L).coerceAtLeast(1L)
    }.getOrNull()
}

private suspend fun tcpLossPercent(target: TcpProbeTarget, attempts: Int, timeoutMs: Int): Int? = withContext(Dispatchers.IO) {
    if (attempts <= 0) return@withContext null
    var fail = 0
    repeat(attempts) {
        val ok = runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target.host, target.port), timeoutMs)
            }
            true
        }.getOrDefault(false)
        if (!ok) fail++
    }
    ((fail.toFloat() / attempts.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
}

@Composable
fun OneKeyCheckScreen(
    modifier: Modifier = Modifier,
    onOpenNetworkSettings: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext

    var networkName by rememberSaveable { mutableStateOf("未知") }
    var networkConnected by rememberSaveable { mutableStateOf(false) }
    var networkDelayMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var packetLossPercent by rememberSaveable { mutableStateOf<Int?>(null) }
    var networkProvince by rememberSaveable { mutableStateOf("未知") }
    var signalRssi by rememberSaveable { mutableStateOf<Int?>(null) }
    var services by remember { mutableStateOf(FALLBACK_SERVICES) }
    val serviceFocusRequesters = remember(services) {
        List(services.size) { FocusRequester() }
    }

    LaunchedEffect(Unit) {
        // 网络名称/信号强度/连接状态：用一键检测 SDK，失败回退本地探测。
        withContext(Dispatchers.IO) {
            runCatching {
                val sig = QuickDetectorSdk.getSignalStrength()
                networkConnected = sig.isConnected
                signalRssi = sig.rssi.takeIf { it != 0 }
                val name = QuickDetectorSdk.getNetWorkName()
                networkName = name.takeIf { it.isNotBlank() }
                    ?: sig.ssid?.takeIf { it.isNotBlank() }
                    ?: bestEffortSsid(context) ?: "未知"
                Log.i(ONE_KEY_TAG, "signal rssi=${sig.rssi} level=${sig.level} ssid=${sig.ssid} connected=${sig.isConnected}")
            }.onFailure {
                Log.w(ONE_KEY_TAG, "getSignalStrength/getNetWorkName failed, fallback", it)
                networkName = bestEffortSsid(context) ?: "未知"
                networkConnected = bestEffortConnected(context)
                signalRssi = bestEffortRssi(context)
            }
        }

        // 网络延迟与丢包：用 SDK ping，失败回退 TCP 探测。
        withContext(Dispatchers.IO) {
            runCatching {
                val r = QuickDetectorSdk.ping("www.baidu.com", 4, 10)
                networkDelayMs = r.avgLatencyMs?.toLong()
                packetLossPercent = r.packetLossRate
                Log.i(ONE_KEY_TAG, "ping avgLatency=${r.avgLatencyMs} loss=${r.packetLossRate}% reachable=${r.isReachable}")
            }.onFailure {
                Log.w(ONE_KEY_TAG, "sdk ping failed, fallback to tcp probe", it)
                val target = TcpProbeTarget(host = "1.1.1.1", port = 443)
                coroutineScope {
                    val latency = async { tcpConnectLatencyMs(target, timeoutMs = 900) }
                    val loss = async { tcpLossPercent(target, attempts = 3, timeoutMs = 700) }
                    networkDelayMs = latency.await()
                    packetLossPercent = loss.await()
                }
            }
        }

        // 网络归属地（省份）：异步回调。
        runCatching {
            QuickDetectorSdk.getLocalNetWorkInfo({ geo ->
                networkProvince = geo?.province?.takeIf { it.isNotBlank() } ?: "未知"
                Log.i(ONE_KEY_TAG, "localNetwork province=${geo?.province} city=${geo?.city} isp=${geo?.isp}")
            }, { code, msg ->
                Log.w(ONE_KEY_TAG, "getLocalNetWorkInfo failed code=$code msg=$msg")
            })
        }

        // 业务套餐绑定信息：异步回调，成功则替换默认列表。
        runCatching {
            QuickDetectorSdk.getBusinessBindInfo({ list ->
                val mapped = list.mapNotNull { businessToServiceItem(it) }
                if (mapped.isNotEmpty()) services = mapped
                Log.i(ONE_KEY_TAG, "businessBindInfo size=${list.size} mapped=${mapped.size}")
            }, { code, msg ->
                Log.w(ONE_KEY_TAG, "getBusinessBindInfo failed code=$code msg=$msg")
            })
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            //.background(Color(0xFFF0F2F5))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "网络检测",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colorResource(R.color.textblack)
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onOpenNetworkSettings,
                        modifier = Modifier.entryFocus(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C73FF), contentColor = Color.White),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Text(text = "网络设置", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCell(title = "网络名称", value = networkName, modifier = Modifier.weight(1f))
                    MetricCell(title = "网络连接", value = if (networkConnected) "成功" else "失败", modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCell(
                        title = "网络延迟",
                        value = networkDelayMs?.let { "${it}ms" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                    MetricCell(
                        title = "丢包率",
                        value = packetLossPercent?.let { "$it%（${qualityLabelForLoss(it)}）" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCell(title = "网络省份", value = networkProvince, modifier = Modifier.weight(1f))
                    MetricCell(
                        title = "信号强度",
                        value = signalRssi?.let { "${it}dBm（${qualityLabelForRssi(it)}）" } ?: "—",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = colorResource(R.color.cardcolor)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "业务绑定信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack)
                )

                services.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val leftIndex = rowIndex * 2
                        val rightIndex = leftIndex + 1
                        ServiceCard(
                            item = rowItems[0],
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(serviceFocusRequesters[leftIndex])
                                .onPreviewKeyEvent {
                                    if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (it.key) {
                                        Key.DirectionRight -> {
                                            serviceFocusRequesters.getOrNull(rightIndex)?.requestFocus()
                                            rowItems.size > 1
                                        }
                                        else -> false
                                    }
                                }
                        )
                        if (rowItems.size > 1) {
                            ServiceCard(
                                item = rowItems[1],
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(serviceFocusRequesters[rightIndex])
                                    .onPreviewKeyEvent {
                                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (it.key) {
                                            Key.DirectionLeft -> {
                                                serviceFocusRequesters[leftIndex].requestFocus()
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private data class ServiceItem(
    val title: String,
    val bound: Boolean,
    val iconRes: Int
)

@Composable
private fun MetricCell(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(text = title, fontSize = 13.sp, color = Color(0xFF6B7280), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 16.sp,
                color = colorResource(R.color.textblack),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServiceCard(item: ServiceItem, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
            .clickable { }
            .focusable()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFF3F4F6), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorResource(R.color.textblack),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (item.bound) Color(0xFF22C55E) else Color(0xFF9CA3AF),
                                RoundedCornerShape(50)
                            )
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (item.bound) "已绑定" else "未开通",
                        fontSize = 13.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.arrow_right),
                contentDescription = null,
                tint = Color(0xFFADB3BD),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

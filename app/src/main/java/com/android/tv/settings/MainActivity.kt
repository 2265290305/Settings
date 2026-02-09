package com.android.tv.settings

import android.net.wifi.WifiConfiguration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.android.tv.settings.ui.theme.设置Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)

        // 隐藏状态栏
        controller.hide(WindowInsetsCompat.Type.statusBars())

        // 可选：下滑临时显示
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        enableEdgeToEdge()
        setContent {
            设置Theme {
                NavigationRailExample()
            }
        }
    }
}


sealed class Destinations(val route: String) {
    object Home : Destinations("home")
    object Detail : Destinations("detail")
    object WifiScreen : Destinations("wifi_screen")
    object AddWifiScreen : Destinations("add_wifi_screen")
    object WifiConnectScreen : Destinations("wifi_connect_screen/{ssid}") {
        fun createRoute(ssid: String) = "wifi_connect_screen/$ssid"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationRailExample(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    val names = stringArrayResource(R.array.docks)
    //val icons = integerArrayResource(R.array.dockicons);
    val startDestination = 0;
    var selectedDestination by rememberSaveable { mutableIntStateOf( startDestination) }
    val tintcolor = Color(0xFF4577FF)
    Scaffold(

        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(

                modifier = modifier.padding(0.dp),
                title = { Text("设置", fontSize = 17.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        //navController.popBackStack()
                    /* 在这里处理返回事件 */ }) {
                        Icon(


                            painter = painterResource(R.drawable.back),

                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { contentPadding ->
        Row(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxHeight(),
                color = NavigationRailDefaults.ContainerColor // 保持和 NavigationRail 一样的背景色
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(top = contentPadding.calculateTopPadding())
                        .padding(vertical = 4.dp) // 给 item 上下加一点边距
                ) {
                    names.forEachIndexed { index, destination ->
                        val isSelected = selectedDestination == index
                        val contentColor = if (isSelected) tintcolor else Color.Unspecified

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .width(150.dp)
                                .height(53.dp)
                                .clickable(
                                    onClick = { selectedDestination = index },
                                    indication = null, // 1. 禁用默认的点击效果（波纹）
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                                .background(
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.account),
                                contentDescription = "",
                                tint = contentColor // 2. 手动控制图标颜色
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = destination,
                                color = contentColor // 3. 手动控制文字颜色
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFF0F0F0)) // 内容区域背景色
                    .padding(contentPadding)
            ) {
                when (selectedDestination) {
                    0 -> PersonalCenterScreen()
                    1 -> {
                        NavHost(navController = navController, startDestination = Destinations.WifiScreen.route) {
                            composable(Destinations.WifiScreen.route) {
                                WifiManagerScreen(navController = navController)
                            }
                            composable(Destinations.AddWifiScreen.route) {
                                AddWifiNetworkScreen(onBack = { navController.popBackStack() })
                            }
                            composable(
                                Destinations.WifiConnectScreen.route,
                                arguments = listOf(navArgument("ssid") { type = NavType.StringType })
                            ) {
                                val ssid = it.arguments?.getString("ssid") ?: ""
                                WifiConnectScreen(ssid = ssid, onBack = { navController.popBackStack() })
                            }
                        }
                    }
                    2->{
                        BlueToothScreen(modifier,navController)
                    }

                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "${names[selectedDestination]} 页面")
                    }
                }
            }
        }
    }
}

@Composable
fun PersonalCenterScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 用户信息卡片
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.account),
                    contentDescription = "Avatar",
                    modifier = Modifier.size(48.dp),
                    tint = Color.Unspecified
                )
                Spacer(Modifier.width(12.dp))
                Text("小翼8899", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("修改", color = Color.Gray, fontSize = 14.sp)
                Spacer(Modifier.width(4.dp))
                //Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Modify", tint = Color.Gray)
            }
        }

        // 账号信息卡片
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("小翼管家账号", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text("189****8899", color = Color.Gray, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { /*TODO*/ }, shape = RoundedCornerShape(50)) {
                    Text("退出登录", color = Color.Red)
                }
            }
        }

        // 二维码卡片
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                /*
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "QR Code",
                    modifier = Modifier.size(160.dp),
                    tint = Color.Black
                )

                 */
                Text("扫码下载“小翼管家”", color = Color.Gray)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WifiManagerScreenPreview() {
    设置Theme {
        //WifiManagerScreen(navController = rememberNavController())
    }
}

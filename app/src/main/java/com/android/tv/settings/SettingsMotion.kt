package com.android.tv.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SettingsPageEnterMotion(
    modifier: Modifier = Modifier,
    heavy: Boolean = false,
    content: @Composable () -> Unit
) {
    // 页面切换的过渡统一由上层 AnimatedContent 做交叉淡入。轻页面直接渲染。
    // 重页面（Wifi/蓝牙/存储/一键检测）首次组合很贵（binder IPC + 长滚动列表），
    // 若与交叉淡入同帧进行会和动画抢主线程导致掉帧。这里让重子树延后到下一帧
    // 再挂载：交叉淡入的头几帧只画空盒子，动画先跑顺，随后再把重内容组合上来，
    // 肉眼几乎无感（约一帧），但能明显消除切页瞬间的卡顿。
    if (!heavy) {
        Box(modifier = modifier) { content() }
        return
    }
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // 等一帧，让上层 AnimatedContent 的进场动画先启动，再挂载重内容。
        withFrameNanos { }
        mounted = true
    }
    Box(modifier = modifier) {
        if (mounted) content()
    }
}

/**
 * 把内容延后到下一帧再挂载：首帧只画占位/版面，重内容（如需同步解码的大图）放到第二帧，
 * 从而压低落地页首帧的 draw 成本。约一帧（~16ms）肉眼无感；[placeholder] 用于占位避免布局跳动。
 */
@Composable
fun DeferToNextFrame(
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        mounted = true
    }
    Box(modifier = modifier) {
        if (mounted) content() else placeholder()
    }
}

private val ShimmerBase = Color(0xFFE6E9EF)
private val ShimmerHighlight = Color(0xFFF7F9FC)

/**
 * 骨架屏微光画刷：一条高亮带从左向右循环扫过，比单纯转圈更贴合“列表内容加载中”的观感。
 * 用一个固定的虚拟横向行程驱动渐变偏移，无需测量实际宽度即可覆盖整行设置项。
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-progress"
    )
    val band = 360f
    val startX = progress * 1800f - band
    return Brush.linearGradient(
        colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
        start = Offset(startX, 0f),
        end = Offset(startX + band, 0f)
    )
}

/** 单条骨架行：白色圆角行内放一个圆形图标占位 + 名称条 + 末尾短状态条，全部用微光画刷填充。 */
@Composable
private fun ShimmerRow(brush: Brush, height: Dp, nameWidth: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .height(height)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(brush)
        )
        Spacer(Modifier.width(14.dp))
        Spacer(
            Modifier
                .height(16.dp)
                .width(nameWidth)
                .clip(RoundedCornerShape(8.dp))
                .background(brush)
        )
        Spacer(Modifier.weight(1f))
        Spacer(
            Modifier
                .height(14.dp)
                .width(48.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(brush)
        )
    }
}

/**
 * 列表加载占位（骨架屏）：延迟一小段时间后再淡入，从而错开页面切换动画，
 * 当加载很快结束时也不会闪一下。相比无限转圈，骨架行能预示即将出现的列表结构，观感更顺滑。
 */
@Composable
fun SettingsLoadingIndicator(
    modifier: Modifier = Modifier,
    appearDelayMillis: Long = 180,
    rows: Int = 3,
    rowHeight: Dp = 60.dp
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(appearDelayMillis)
        show = true
    }
    AnimatedVisibility(
        visible = show,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 200)),
        exit = fadeOut(animationSpec = tween(durationMillis = 100))
    ) {
        val brush = rememberShimmerBrush()
        // 名称条宽度交错变化，让骨架行看起来更自然，不像整齐的复制粘贴。
        val nameWidths = listOf(168.dp, 120.dp, 196.dp, 140.dp)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(rows) { index ->
                ShimmerRow(
                    brush = brush,
                    height = rowHeight,
                    nameWidth = nameWidths[index % nameWidths.size]
                )
            }
        }
    }
}

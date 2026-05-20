package edu.cqwu.electricity.ui.qrcode

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import edu.cqwu.electricity.ui.theme.LocalQrCodeSettings
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Job
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.data.local.QrCodeColorMode
import edu.cqwu.electricity.data.network.ApiConfig
import edu.cqwu.electricity.data.network.CookieStore
import edu.cqwu.electricity.data.network.ElectricityApi
import edu.cqwu.electricity.data.network.QrCodeApi
import edu.cqwu.electricity.data.network.QrCodeType
import edu.cqwu.electricity.data.network.SessionExpiredException
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.QrCodeView
import edu.cqwu.electricity.ui.components.ReLoginContent
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 乘车码/支付码原生二维码显示页面
 *
 * 通过已登录的 Session 实时获取二维码数据，使用 ZXing 生成 QR 码原生显示。
 * 特性：
 * - 支持右上角刷新按钮和下拉刷新
 * - 每 30 秒自动静默刷新二维码
 * - Session 过期时显示「重新登录」按钮（与其他页面一致）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeDisplayScreen(
    qrCodeType: QrCodeType,
    title: String,
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    onNavigateToQrCodeSettings: () -> Unit = {},
) {
    var qrCodeContent by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val qrCodeSettings = LocalQrCodeSettings.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // 深色模式下背景强制白色（确保扫码可读），前景色遵循用户设置
    val qrPrimaryColor = when (qrCodeSettings.colorMode) {
        QrCodeColorMode.THEME_SNAKE -> MaterialTheme.colorScheme.primary
        QrCodeColorMode.MONOCHROME -> Color.Black
    }
    // 深色模式下 MD3 primary 偏浅（为深色背景设计），在白色背景上需加深
    val qrEffectivePrimaryColor = if (isDarkTheme && qrCodeSettings.colorMode == QrCodeColorMode.THEME_SNAKE) {
        val c = qrPrimaryColor
        val darkenFactor = 0.45f
        Color(c.red * darkenFactor, c.green * darkenFactor, c.blue * darkenFactor, c.alpha)
    } else {
        qrPrimaryColor
    }
    val qrBackgroundColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.surface
    val qrCornerFraction = qrCodeSettings.cornerRadius / 100f
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var requiresReLogin by rememberSaveable { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    // 倒计时秒数
    var countdownSeconds by rememberSaveable { mutableIntStateOf(30) }
    // 余额
    var balance by rememberSaveable { mutableStateOf<String?>(null) }
    // 前台状态（防止后台时继续倒计费和请求）
    var isAppInForeground by remember { mutableStateOf(true) }
    // 刷新请求 Job（用于取消上一个未完成的请求，避免并发）
    var fetchJob by remember { mutableStateOf<Job?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val qrCodeApi = remember { QrCodeApi() }
    val api = remember { ElectricityApi() }

    // 窗口亮度控制：根据设置决定是否调高屏幕亮度
    val window = remember(context) { (context as? Activity)?.window }
    val isScreenBrightnessEnabled = qrCodeSettings.screenBrightnessEnabled
    DisposableEffect(isScreenBrightnessEnabled) {
        if (isScreenBrightnessEnabled) {
            window?.let { win ->
                val lp = win.attributes
                lp.screenBrightness = 1.0f
                win.attributes = lp
            }
        }
        onDispose {
            if (isScreenBrightnessEnabled) {
                window?.let { win ->
                    val lp = win.attributes
                    lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    win.attributes = lp
                }
            }
        }
    }

    // 获取账户余额
    fun fetchBalance() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                api.fetchAccountInfo()
            }
            result.onSuccess { info ->
                val numericBalance = info.balance.replace(Regex("[^0-9.]"), "")
                balance = numericBalance
            }.onFailure {
                // 余额获取失败仅静默处理
            }
        }
    }

    // 获取二维码数据
    fun fetchQrCode() {
        fetchJob?.cancel()  // 取消上一个未完成的请求，避免并发
        fetchJob = scope.launch {
            val isInitialLoad = qrCodeContent == null
            if (isInitialLoad) {
                isLoading = true
            }
            errorMessage = null
            requiresReLogin = false

            // ═══ 本地预检查：发起 HTTP 请求前先检查 CookieManager 中是否有有效的 CASTGC ═══
            // 避免在 CASTGC 不存在或已过期时仍然走完完整的 CAS 重定向链（耗时 ~15s）
            val castgc = withContext(Dispatchers.IO) {
                CookieStore.getCookieValue(ApiConfig.LOGIN_URL, "CASTGC")
            }
            if (castgc == null) {
                isLoading = false
                isRefreshing = false
                requiresReLogin = true
                errorMessage = "登录已过期，请重新登录"
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                qrCodeApi.fetchQrCode(qrCodeType)
            }

            result.onSuccess { content ->
                qrCodeContent = content
                isLoading = false
                isRefreshing = false
                countdownSeconds = 30
                // 二维码获取成功后同步获取余额
                fetchBalance()
            }.onFailure { error ->
                isLoading = false
                isRefreshing = false
                if (error is SessionExpiredException) {
                    requiresReLogin = true
                    errorMessage = "登录已过期，请重新登录"
                } else {
                    errorMessage = error.message ?: "获取二维码失败"
                }
            }
        }
    }

    // 监听 Activity 生命周期：从后台/锁屏切回前台时刷新二维码
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground = true
                    // 已有二维码内容时刷新（避免首次加载时重复请求）
                    if (qrCodeContent != null) {
                        fetchQrCode()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isAppInForeground = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 初始加载
    LaunchedEffect(qrCodeType) {
        fetchQrCode()
    }

    // 定时自动刷新：每 30 秒静默刷新一次（仅在应用在前台时运行）
    LaunchedEffect(qrCodeType) {
        while (isActive) {
            delay(1000)
            if (!isAppInForeground) continue  // 后台时不倒数也不刷新
            countdownSeconds--
            if (countdownSeconds <= 0) {
                countdownSeconds = 30
                // 仅在已有二维码且未过期时静默刷新
                if (qrCodeContent != null && !requiresReLogin) {
                    val result = withContext(Dispatchers.IO) {
                        qrCodeApi.fetchQrCode(qrCodeType)
                    }
                    result.onSuccess { content ->
                        qrCodeContent = content
                    }.onFailure { error ->
                        val msg = error.message ?: "刷新失败"
                        if (error is SessionExpiredException) {
                            requiresReLogin = true
                            errorMessage = "登录已过期，请重新登录"
                        } else {
                            snackbar.show(msg, ToastUtils.Type.ERROR)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Cookie 状态指示器（绿色=有效，红色=过期）
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (!requiresReLogin && !isLoading)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.error
                                )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { fetchQrCode() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToQrCodeSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "二维码设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = topBarColors
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading && qrCodeContent == null -> {
                    PullToRefreshBox(
                        isRefreshing = true,
                        onRefresh = {
                            isRefreshing = true
                            errorMessage = null
                            fetchQrCode()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
                errorMessage != null && qrCodeContent == null -> {
                    // 错误状态：Session 过期显示重新登录，否则显示下拉重试
                    if (requiresReLogin) {
                        ReLoginContent(
                            errorMessage = errorMessage,
                            requiresReLogin = true,
                            onReLogin = onNavigateToLogin,
                            onRetry = {
                                requiresReLogin = false
                                isLoading = true
                                errorMessage = null
                                fetchQrCode()
                            },
                        )
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                isRefreshing = true
                                errorMessage = null
                                isLoading = true
                                fetchQrCode()
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = errorMessage ?: "加载失败",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "下拉或点击刷新按钮重试",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
                qrCodeContent != null -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            fetchQrCode()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Spacer(modifier = Modifier.height(48.dp))

                            // 余额显示
                            Box(
                                modifier = Modifier.height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (balance != null) {
                                    Text(
                                        text = "剩余 ${balance} ￥",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // 二维码（深色模式下使用白底黑块确保扫码设备可读）
                            qrCodeContent?.let { content ->
                                QrCodeView(
                                    content = content,
                                    modifier = Modifier.size(320.dp),
                                    squareCornerFraction = qrCornerFraction,
                                    primaryColor = qrEffectivePrimaryColor,
                                    backgroundColor = qrBackgroundColor,
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = "请将二维码对准扫码设备",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 自动刷新倒计时
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "自动刷新",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${countdownSeconds}s",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 二维码字符串
                            qrCodeContent?.let { displayContent ->
                                Text(
                                    text = displayContent,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }

}

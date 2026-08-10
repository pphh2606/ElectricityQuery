package edu.cqwu.electricity.qrcode.ui

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.cardcenter.data.CardCenterApi
import edu.cqwu.electricity.login.data.CasAuthApi
import edu.cqwu.electricity.login.data.CookieParser
import edu.cqwu.electricity.login.data.CookieStore
import edu.cqwu.electricity.login.data.SessionExpiredException
import edu.cqwu.electricity.qrcode.data.QrCodeApi
import edu.cqwu.electricity.qrcode.data.QrCodeType
import edu.cqwu.electricity.settings.data.QrCodeColorMode
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalQrCodeSettings
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.LocalTopBarState
import edu.cqwu.electricity.theme.ui.QrCodeView
import edu.cqwu.electricity.theme.ui.ReLoginContent
import edu.cqwu.electricity.theme.ui.toTopAppBarColors
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
) {
    var qrCodeContent by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val nav = LocalNavController.current
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
        val darkenFactor = 0.45f
        Color(
            qrPrimaryColor.red * darkenFactor,
            qrPrimaryColor.green * darkenFactor, qrPrimaryColor.blue * darkenFactor,
            qrPrimaryColor.alpha)
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
    // 标记是否真正进入后台（按 Home 键/锁屏），用于避免子页面 pop 回来重复刷新
    var wasBackgrounded by remember { mutableStateOf(false) }
    // 刷新请求 Job（用于取消上一个未完成的请求，避免并发）
    var fetchJob by remember { mutableStateOf<Job?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = LocalSnackbarController.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val qrCodeApi = remember { QrCodeApi() }
    val api = remember { CardCenterApi() }

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
                CookieParser.getValue(CookieStore.getCookie(CasAuthApi.LOGIN_URL), "CASTGC")
            }
            if (castgc == null) {
                isLoading = false
                isRefreshing = false
                requiresReLogin = true
                errorMessage = resources.getString(R.string.qrcode_login_expired)
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
                    errorMessage = resources.getString(R.string.qrcode_login_expired)
                } else {
                    errorMessage = error.message ?: resources.getString(R.string.qrcode_fetch_failed)
                }
            }
        }
    }

    // 监听 Activity 生命周期：从后台/锁屏切回前台时刷新二维码
    // 注意使用 ON_STOP 而非 ON_PAUSE 来区分"真正进入后台"和"导航到子页面"，
    // 避免用户从设置页返回时二维码被重复刷新。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground = true
                    // 仅当确实从后台（按 Home 键/锁屏）恢复时才刷新，
                    // 避免子页面 pop 回来重复触发
                    if (wasBackgrounded && qrCodeContent != null) {
                        fetchQrCode()
                        wasBackgrounded = false
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    isAppInForeground = false
                    wasBackgrounded = true
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
                        val msg = error.message ?: resources.getString(R.string.qrcode_refresh_failed)
                        if (error is SessionExpiredException) {
                            requiresReLogin = true
                            errorMessage = resources.getString(R.string.qrcode_login_expired)
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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { fetchQrCode() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.common_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { nav.navigate(Routes.QR_CODE_SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.qrcode_display_settings),
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
                            onReLogin = { nav.navigate(Routes.LOGIN) },
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
                                        text = errorMessage ?: stringResource(R.string.webview_error_load_failed),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.common_pull_to_retry),
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
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Spacer(modifier = Modifier.height(48.dp))

                            // 扫描提示（顶部大字）
                            Text(
                                text = stringResource(R.string.qrcode_display_scan_hint),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 自动刷新倒计时（小字，顶部）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.qrcode_display_auto_refresh),
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

                            // 余额（二维码下方，始终占位避免加载前后 UI 移位）
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(
                                modifier = Modifier.height(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (balance != null) {
                                    Text(
                                        text = stringResource(R.string.qrcode_display_balance, balance ?: "0.00"),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // 二维码内容（灰色小字，长按可复制）
                            SelectionContainer {
                                Text(
                                    text = qrCodeContent ?: "",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        lineBreak = LineBreak.Simple
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // 底部按钮区：充值 | 订单记录
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { nav.navigate(Routes.CARD_RECHARGE) }) {
                                    Text(
                                        stringResource(R.string.recharge_pay_now),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }

                                Text(
                                    text = "|",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )

                                TextButton(onClick = { nav.navigate(Routes.BILL) }) {
                                    Text(
                                        stringResource(R.string.qrcode_display_orders),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

}

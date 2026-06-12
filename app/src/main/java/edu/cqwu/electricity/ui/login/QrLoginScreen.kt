package edu.cqwu.electricity.ui.login

import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import edu.cqwu.electricity.data.local.AccountStore
import edu.cqwu.electricity.data.local.QrCodeColorMode
import edu.cqwu.electricity.data.network.QrLoginApi
import edu.cqwu.electricity.ui.components.LocalSnackbarController
import edu.cqwu.electricity.ui.components.QrCodeView
import edu.cqwu.electricity.ui.theme.LocalQrCodeSettings
import edu.cqwu.electricity.ui.theme.LocalTopBarState
import edu.cqwu.electricity.ui.theme.toTopAppBarColors
import edu.cqwu.electricity.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 扫码登录页面 UI 状态
 */
private sealed class QrLoginUiState {
    data object Initializing : QrLoginUiState()        // 初始化中（由 PullToRefreshBox 指示器替代加载动画）
    data class Ready(val lt: String, val execution: String) : QrLoginUiState()  // 已获取二维码
    data object Scanned : QrLoginUiState()             // 已扫码，待确认
    data object Confirmed : QrLoginUiState()           // 已确认，正在提交
    data class Error(val message: String) : QrLoginUiState()  // 错误
}

/**
 * 扫码登录页面。
 *
 * 流程：
 * 1. 获取登录页 → 解析 lt/execution
 * 2. 获取二维码 UUID
 * 3. 显示二维码图片
 * 4. 每 1 秒轮询扫码状态
 * 5. 确认后提交认证 → 获取 CASTGC
 * 6. 登录成功，自动返回
 *
 * 支持下拉刷新重新获取二维码。
 * 下拉刷新覆盖区域 = Scaffold 内容区（标题栏以下全部区域），确保加载失败时仍可下拉重试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrLoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    onNavigateToQrCodeSettings: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { QrLoginApi() }
    val topBarColors = LocalTopBarState.current.style.toTopAppBarColors(MaterialTheme.colorScheme)
    val snackbar = LocalSnackbarController.current

    var uiState by remember { mutableStateOf<QrLoginUiState>(QrLoginUiState.Initializing) }
    var isRefreshing by remember { mutableStateOf(false) }
    // 解码后的二维码内容字符串（用于本地 QrCodeView 渲染）
    var qrCodeDecodedContent by remember { mutableStateOf<String?>(null) }

    // ── 二维码主题设置（与支付码页面一致）──
    val qrCodeSettings = LocalQrCodeSettings.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val qrPrimaryColor = when (qrCodeSettings.colorMode) {
        QrCodeColorMode.THEME_SNAKE -> MaterialTheme.colorScheme.primary
        QrCodeColorMode.MONOCHROME -> Color.Black
    }
    val qrEffectivePrimaryColor = if (isDarkTheme && qrCodeSettings.colorMode == QrCodeColorMode.THEME_SNAKE) {
        Color(
            qrPrimaryColor.red * 0.45f, qrPrimaryColor.green * 0.45f,
            qrPrimaryColor.blue * 0.45f, qrPrimaryColor.alpha)
    } else {
        qrPrimaryColor
    }
    val qrBackgroundColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.surface
    val qrCornerFraction = qrCodeSettings.cornerRadius / 100f

    // 窗口亮度控制：根据设置决定是否调高屏幕亮度（与支付码页面一致）
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

    /**
     * 启动/重启扫码登录流程。
     * 被 LaunchedEffect(Unit) 首次自动调用，
     * 也被下拉刷新的 onRefresh 回调调用。
     */
    fun startQrLogin() {
        scope.launch {
            isRefreshing = true
            uiState = QrLoginUiState.Initializing
            qrCodeDecodedContent = null

            // Step 1: 获取登录页，解析 lt/execution
            val pageResult = api.fetchLoginPage()
            if (pageResult.isFailure) {
                uiState = QrLoginUiState.Error(pageResult.exceptionOrNull()?.message ?: context.getString(R.string.login_get_page_failed))
                isRefreshing = false
                return@launch
            }
            val pageData = pageResult.getOrThrow()

            // Step 2: 获取二维码 UUID
            val uuidResult = api.fetchQrCodeUuid()
            if (uuidResult.isFailure) {
                uiState = QrLoginUiState.Error(uuidResult.exceptionOrNull()?.message ?: context.getString(R.string.qrcode_fetch_failed))
                isRefreshing = false
                return@launch
            }
            val uuid = uuidResult.getOrThrow()

            // Step 2.5: 下载二维码图片并解码为内容字符串
            val decodeResult = api.downloadAndDecodeQrCode(uuid)
            if (decodeResult.isFailure) {
                uiState = QrLoginUiState.Error(decodeResult.exceptionOrNull()?.message ?: context.getString(R.string.qrcode_decode_failed))
                isRefreshing = false
                return@launch
            }
            qrCodeDecodedContent = decodeResult.getOrThrow()

            uiState = QrLoginUiState.Ready(lt = pageData.lt, execution = pageData.execution)
            isRefreshing = false

            // Step 3: 轮询扫码状态（与 uiState 解耦，始终运行直到 break）
            while (true) {
                delay(1000) // 每 1 秒轮询一次
                val statusResult = api.pollQrCodeStatus(uuid)
                if (statusResult.isFailure) continue

                val status = statusResult.getOrThrow()
                when (status) {
                    "0" -> { /* 等待扫码，继续轮询 */ }
                    "2" -> {
                        uiState = QrLoginUiState.Scanned
                        // 继续等待确认（轮询不停止，等待 status 变为 "1"）
                    }
                    "1" -> {
                        uiState = QrLoginUiState.Confirmed
                        val submitResult = api.submitQrLogin(pageData.lt, uuid, pageData.execution)
                        if (submitResult.isSuccess) {
                            val loginResult = submitResult.getOrThrow()
                            if (loginResult.username.isNotBlank()) {
                                try {
                                    val accountStore = AccountStore(context)
                                    accountStore.saveAccount(
                                        username = loginResult.username,
                                        password = null,
                                        rememberPassword = false
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.w("QrLoginScreen", "保存用户到 AccountStore 失败", e)
                                }
                            }
                            snackbar.show(context.getString(R.string.login_success), ToastUtils.Type.SUCCESS)
                            onLoginSuccess()
                        } else {
                            uiState = QrLoginUiState.Error(
                                submitResult.exceptionOrNull()?.message ?: context.getString(R.string.login_failed)
                            )
                        }
                        break
                    }
                    "3" -> {
                        uiState = QrLoginUiState.Error(context.getString(R.string.login_qr_expired))
                        break
                    }
                }
            }
        }
    }

    // ═══ 首次启动：自动开始扫码登录流程 ═══
    var firstStart by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (firstStart) {
            firstStart = false
            startQrLogin()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.qr_login_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToQrCodeSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.qrcode_display_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = topBarColors,
            )
        },
    ) { paddingValues ->
        // PullToRefreshBox 覆盖整个 Scaffold 内容区（标题栏以下全部区域）
        // 确保在 Initializing / Error 状态下下拉也生效
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { startQrLogin() },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                when (val state = uiState) {
                    is QrLoginUiState.Initializing -> {
                        // 初始状态不显示额外加载动画（PullToRefreshBox 的指示器已足够）
                        // 显示提示文字让用户知道正在加载
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.qr_login_fetching_qrcode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is QrLoginUiState.Ready -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // 使用本地 QrCodeView 渲染二维码（与支付码页面一致）
                            // 支持用户自定义的颜色模式、圆角等主题设置
                            Box(
                                modifier = Modifier
                                    .size(320.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                qrCodeDecodedContent?.let { content ->
                                    QrCodeView(
                                        content = content,
                                        modifier = Modifier.fillMaxSize(),
                                        squareCornerFraction = qrCornerFraction,
                                        primaryColor = qrEffectivePrimaryColor,
                                        backgroundColor = qrBackgroundColor,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Text(
                                text = stringResource(R.string.qr_login_scan_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.qr_login_expiry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            // 底部按钮区：保存到相册 | 其他应用打开
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 保存到相册
                                TextButton(
                                    onClick = {
                                        val content = qrCodeDecodedContent
                                        if (!content.isNullOrBlank()) {
                                            scope.launch {
                                                val success = withContext(Dispatchers.IO) {
                                                    saveQrCodeToGallery(context, content)
                                                }
                                                if (success) {
                                                    snackbar.show(context.getString(R.string.qrcode_save_success), ToastUtils.Type.SUCCESS)
                                                } else {
                                                    snackbar.show(context.getString(R.string.qrcode_save_failed), ToastUtils.Type.ERROR)
                                                }
                                            }
                                        }
                                    },
                                    enabled = !qrCodeDecodedContent.isNullOrBlank(),
                                ) {
                                    Text(
                                        stringResource(R.string.scan_save_to_album),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }

                                Text(
                                    text = "|",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                )

                                // 其他应用打开
                                TextButton(
                                    onClick = {
                                        val url = qrCodeDecodedContent?.trim()
                                        if (!url.isNullOrBlank()) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.w("QrLoginScreen", "打开URL失败", e)
                                            }
                                        }
                                    },
                                    enabled = !qrCodeDecodedContent.isNullOrBlank(),
                                ) {
                                    Text(
                                        stringResource(R.string.scan_open_external),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }

                    is QrLoginUiState.Scanned -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.qr_login_scan_success),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.qr_login_confirm_on_phone),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is QrLoginUiState.Confirmed -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.qr_login_completing),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is QrLoginUiState.Error -> {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.common_pull_to_retry),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== 辅助函数 ====================

/**
 * 生成二维码 Bitmap。
 * 使用 ZXing QRCodeWriter 编码内容，生成指定尺寸的纯黑白二维码图片。
 */
private fun generateQrCodeBitmap(content: String, size: Int): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val w = bitMatrix.width
        val h = bitMatrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                pixels[y * w + x] = if (bitMatrix[x, y]) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.RGB_565)
    } catch (e: Exception) {
        android.util.Log.w("QrLoginScreen", "生成二维码 Bitmap 失败", e)
        null
    }
}

/**
 * 将二维码内容生成为图片并保存到系统相册。
 *
 * 使用 MediaStore API 保存（API 29+ 无需存储权限），
 * 保存到 Pictures 目录下的 ElectricBill 文件夹。
 *
 * @return true 表示保存成功，false 表示保存失败
 */
private fun saveQrCodeToGallery(context: Context, content: String): Boolean {
    return try {
        val bitmap = generateQrCodeBitmap(content, 512) ?: return false

        val filename = "QR_Login_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ElectricBill")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ) ?: return false

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(CompressFormat.PNG, 100, outputStream)
            outputStream.flush()
        }

        bitmap.recycle()
        true
    } catch (e: Exception) {
        android.util.Log.w("QrLoginScreen", "保存二维码到相册失败", e)
        false
    }
}

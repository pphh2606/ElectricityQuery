package edu.cqwu.electricity.login.ui

import edu.cqwu.electricity.theme.ui.currentTopBarColors

import edu.cqwu.electricity.logging.AppLog

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.os.Build
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import edu.cqwu.electricity.R
import edu.cqwu.electricity.settings.data.QrCodeColorMode
import edu.cqwu.electricity.theme.ui.LocalAppSettingsState
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.QrCodeView
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.theme.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 扫码登录页面（纯 UI）。
 *
 * 取码 → 轮询 → 提交会话的流程与状态见 [QrLoginViewModel]；
 * 本页面只负责渲染状态、下拉刷新重启流程、处理登录成功事件。
 *
 * 下拉刷新覆盖区域 = Scaffold 内容区（标题栏以下全部区域），确保加载失败时仍可下拉重试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrLoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    onNavigateToQrCodeSettings: () -> Unit = {},
    viewModel: QrLoginViewModel = viewModel(),
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val topBarColors = currentTopBarColors()
    val snackbar = LocalSnackbarController.current

    // 状态与流程统一由 ViewModel 驱动（取码/轮询/提交会话在其内部完成）
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    // 一次性事件：登录成功 → 提示并返回上一页
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == QrLoginEvent.LoginSuccess) {
                snackbar.show(resources.getString(R.string.login_success), ToastUtils.Type.SUCCESS)
                onLoginSuccess()
            }
        }
    }

    // ── 二维码主题设置（与支付码页面一致）──
    val appSettings = LocalAppSettingsState.current
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val qrPrimaryColor = when (appSettings.qrCodeColorMode) {
        QrCodeColorMode.THEME_SNAKE -> MaterialTheme.colorScheme.primary
        QrCodeColorMode.MONOCHROME -> Color.Black
    }
    val qrEffectivePrimaryColor = if (isDarkTheme && appSettings.qrCodeColorMode == QrCodeColorMode.THEME_SNAKE) {
        Color(
            qrPrimaryColor.red * 0.45f, qrPrimaryColor.green * 0.45f,
            qrPrimaryColor.blue * 0.45f, qrPrimaryColor.alpha)
    } else {
        qrPrimaryColor
    }
    val qrBackgroundColor = if (isDarkTheme) Color.White else MaterialTheme.colorScheme.surface
    val qrCornerFraction = appSettings.effectiveQrCodeCornerRadius / 100f

    // 窗口亮度控制：根据设置决定是否调高屏幕亮度（与支付码页面一致）
    val window = remember(context) { (context as? Activity)?.window }
    val isScreenBrightnessEnabled = appSettings.qrScreenBrightnessEnabled
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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToQrCodeSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
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
            onRefresh = viewModel::startQrLogin,
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
                            // 提示文字（二维码上方）
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

                            Spacer(modifier = Modifier.height(24.dp))

                            // 使用本地 QrCodeView 渲染二维码（与支付码页面一致）
                            // 支持用户自定义的颜色模式、圆角等主题设置
                            Box(
                                modifier = Modifier
                                    .size(320.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                QrCodeView(
                                    content = state.content,
                                    modifier = Modifier.fillMaxSize(),
                                    squareCornerFraction = qrCornerFraction,
                                    primaryColor = qrEffectivePrimaryColor,
                                    backgroundColor = qrBackgroundColor,
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 二维码网址内容（灰色小字，居中，长按可复制）
                            SelectionContainer {
                                Text(
                                    text = state.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                )
                            }

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
                                        scope.launch {
                                            val success = withContext(Dispatchers.IO) {
                                                saveQrCodeToGallery(context, state.content)
                                            }
                                            if (success) {
                                                snackbar.show(resources.getString(R.string.qrcode_save_success), ToastUtils.Type.SUCCESS)
                                            } else {
                                                snackbar.show(resources.getString(R.string.qrcode_save_failed), ToastUtils.Type.ERROR)
                                            }
                                        }
                                    },
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

                                // 分享网址
                                TextButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, state.content)
                                            }
                                            context.startActivity(Intent.createChooser(intent, null))
                                        } catch (e: Exception) {
                                            AppLog.w("QrLoginScreen", "分享URL失败", e)
                                        }
                                    },
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
                        ReLoginContent(
                            errorMessage = state.message,
                            requiresReLogin = false,
                            onReLogin = {},
                            onRetry = viewModel::startQrLogin,
                        )
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
private fun generateQrCodeBitmap(content: String): Bitmap? {
    return try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
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
        AppLog.w("QrLoginScreen", "生成二维码 Bitmap 失败", e)
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return false
    }
    return try {
        val bitmap = generateQrCodeBitmap(content) ?: return false

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
        AppLog.w("QrLoginScreen", "保存二维码到相册失败", e)
        false
    }
}

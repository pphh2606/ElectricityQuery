package edu.cqwu.electricity.scan.ui

import android.annotation.SuppressLint
import androidx.compose.ui.res.stringResource
import edu.cqwu.electricity.R

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.URLUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import edu.cqwu.electricity.theme.ui.LocalSnackbarController

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private const val TAG = "ScanScreen"
private const val SCAN_FRAME_RATIO = 0.7f
private const val ANALYSIS_WIDTH = 1280
private const val ANALYSIS_HEIGHT = 720
private const val QR_DECODE_MAX_DIMENSION = 1024

// ════════════════════════════════════════════════════════════════════════
// 权限状态机
// ════════════════════════════════════════════════════════════════════════

private sealed class CameraPermissionState {
    data object Unknown : CameraPermissionState()
    data object Granted : CameraPermissionState()
    data object Denied : CameraPermissionState()
    data object PermanentlyDenied : CameraPermissionState()
}

// ════════════════════════════════════════════════════════════════════════
// ImageAnalysis 分析器
// ════════════════════════════════════════════════════════════════════════

/**
 * CameraX [ImageAnalysis.Analyzer]，将每一帧 YUV_420_888 数据通过
 * [PlanarYUVLuminanceSource] 转为 ZXing 的 [BinaryBitmap] 并尝试解码。
 *
 * 使用 ThreadLocal 复用 ByteArray 以降低 GC 压力。
 */
private class QrCodeAnalyzer(
    private val previewView: PreviewView,
    private val scanFrameFraction: Float,
    private val isPaused: () -> Boolean,
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    /** 线程局部 ByteArray 复用缓冲区 */
    private val threadLocalBuffer = object : ThreadLocal<ByteArray>() {
        override fun initialValue(): ByteArray? = null
    }

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (isPaused()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val planes = mediaImage.planes
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val rowStride = yPlane.rowStride
        val size = yBuffer.remaining()

        // 复用 ByteArray，避免每帧分配
        val yData = threadLocalBuffer.get()?.takeIf { it.size >= size }
            ?: ByteArray(size).also { threadLocalBuffer.set(it) }
        yBuffer.get(yData)

        val analysisWidth = imageProxy.width
        val analysisHeight = imageProxy.height

        val cropRect = calculateCropRect(previewView, analysisWidth, analysisHeight, scanFrameFraction)

        val source = PlanarYUVLuminanceSource(
            yData,
            rowStride,
            analysisHeight,
            cropRect.left, cropRect.top,
            cropRect.width(), cropRect.height(),
            false,
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        try {
            val result = reader.decode(binaryBitmap)
            onResult(result.text)
        } catch (_: NotFoundException) {
            // 正常
        } catch (_: ChecksumException) {
            // 正常
        } catch (_: FormatException) {
            // 正常
        } finally {
            imageProxy.close()
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// 扫码框 UI 坐标 → Camera 坐标映射
// ════════════════════════════════════════════════════════════════════════

private fun calculateCropRect(
    previewView: PreviewView,
    analysisWidth: Int,
    analysisHeight: Int,
    scanFrameFraction: Float,
): Rect {
    val previewWidth = previewView.width.toFloat()
    val previewHeight = previewView.height.toFloat()

    if (previewWidth <= 0 || previewHeight <= 0 || analysisWidth <= 0 || analysisHeight <= 0) {
        return Rect(0, 0, analysisWidth, analysisHeight)
    }

    val previewAspect = previewWidth / previewHeight
    val analysisAspect = analysisWidth.toFloat() / analysisHeight

    val scaledWidth: Float
    val scaledHeight: Float
    if (previewAspect > analysisAspect) {
        scaledHeight = previewHeight
        scaledWidth = previewHeight * analysisAspect
    } else {
        scaledWidth = previewWidth
        scaledHeight = previewWidth / analysisAspect
    }

    val offsetX = (previewWidth - scaledWidth) / 2
    val offsetY = (previewHeight - scaledHeight) / 2

    val frameSize = minOf(previewWidth, previewHeight) * scanFrameFraction
    val frameLeft = (previewWidth - frameSize) / 2
    val frameTop = (previewHeight - frameSize) / 2

    val cropLeft = ((frameLeft - offsetX) / scaledWidth * analysisWidth).toInt()
        .coerceIn(0, analysisWidth)
    val cropTop = ((frameTop - offsetY) / scaledHeight * analysisHeight).toInt()
        .coerceIn(0, analysisHeight)
    val cropWidth = (frameSize / scaledWidth * analysisWidth).toInt()
        .coerceIn(1, analysisWidth - cropLeft)
    val cropHeight = (frameSize / scaledHeight * analysisHeight).toInt()
        .coerceIn(1, analysisHeight - cropTop)

    return Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
}

// ════════════════════════════════════════════════════════════════════════
// 从相册图片解码二维码（防 OOM + 主动回收）
// ════════════════════════════════════════════════════════════════════════

private fun decodeQrFromUri(context: Context, uri: Uri): String? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }

        val sampleSize = computeInSampleSize(opts.outWidth, opts.outHeight)

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle() // 解码完成后主动回收

        val source = RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
        val result = MultiFormatReader().decode(binaryBitmap)
        result.text
    } catch (e: NotFoundException) {
        Log.w(TAG, "decodeQrFromUri: no QR found in image")
        null
    } catch (e: Exception) {
        Log.e(TAG, "decodeQrFromUri failed", e)
        null
    }
}

private fun computeInSampleSize(
    rawWidth: Int,
    rawHeight: Int,
): Int {
    var inSampleSize = 1
    if (rawHeight > QR_DECODE_MAX_DIMENSION || rawWidth > QR_DECODE_MAX_DIMENSION) {
        val halfHeight = rawHeight / 2
        val halfWidth = rawWidth / 2
        while (halfHeight / inSampleSize >= QR_DECODE_MAX_DIMENSION && halfWidth / inSampleSize >= QR_DECODE_MAX_DIMENSION) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

// ════════════════════════════════════════════════════════════════════════
// 扫码结果处理
// ════════════════════════════════════════════════════════════════════════

private fun handleScanResult(
    text: String,
    context: Context,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onResumeScan: () -> Unit,
    showSnackbar: (message: String) -> Unit,
) {
    when {
        URLUtil.isValidUrl(text) -> {
            onOpenUrl(text)
        }
        text.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) -> {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(text))
                context.startActivity(intent)
                onBack()
            } catch (e: ActivityNotFoundException) {
                showSnackbar(context.getString(R.string.qrcode_cannot_handle_link, text))
                onResumeScan()
            }
        }
        else -> {
            showSnackbar(context.getString(R.string.qrcode_scan_result, text))
            onBack()
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// ScanScreen Composable
// ════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onOpenUrl: (url: String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
    val snackbar = LocalSnackbarController.current

    // ── 响应式权限状态（可观察，非 remember-only） ──
    var permissionState by remember {
        mutableStateOf(
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) CameraPermissionState.Granted
            else CameraPermissionState.Unknown
        )
    }

    // 监听生命周期 ON_RESUME：从系统设置返回后自动刷新权限
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED -> CameraPermissionState.Granted
                    else -> CameraPermissionState.Denied
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── CameraX 提供者实例（避免在 dispose 时阻塞主线程） ──
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // ── 其他状态 ──
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by rememberSaveable { mutableStateOf(false) }
    var isScanPaused by remember { mutableStateOf(false) }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }

    // ── 权限请求 Launcher ──
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = if (granted) {
            CameraPermissionState.Granted
        } else {
            val activity = context.findActivity()
            val canRequestAgain = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (canRequestAgain) {
                CameraPermissionState.Denied
            } else {
                CameraPermissionState.PermanentlyDenied
            }
        }
    }

    // ★ P0 fix: 权限状态变化时在 LaunchedEffect 中触发请求，而非 UI 分支
    LaunchedEffect(permissionState) {
        if (permissionState is CameraPermissionState.Unknown) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // ── 相册选择 ──
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isScanPaused = true
            val text = decodeQrFromUri(context, it)
            if (text != null) {
                handleScanResult(text, context, onOpenUrl, onBack, { isScanPaused = false }) { msg -> snackbar.show(msg) }
            } else {
                snackbar.show(resources.getString(R.string.qrcode_no_result))
                isScanPaused = false
            }
        }
    }

    fun resumeScan() {
        isScanPaused = false
    }

    // ── 手电筒状态与相机同步 ──
    DisposableEffect(camera, isTorchOn) {
        camera?.cameraControl?.enableTorch(isTorchOn)
        onDispose { }
    }

    // ── 相机生命周期（CameraX 绑定/解绑） ──
    // key 包含 permissionState：权限授予后重新触发绑定，避免首次授权后相机预览不出来
    DisposableEffect(lifecycleOwner, permissionState) {
        // 权限未授予时不绑定相机，等待权限授予后 key 变化重新触发
        if (permissionState != CameraPermissionState.Granted) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val executor = ContextCompat.getMainExecutor(context)

            cameraProviderFuture.addListener({
                val provider = cameraProviderFuture.get()
                cameraProvider = provider // 保存实例，避免 dispose 时阻塞

                val preview = Preview.Builder().build()
                preview.surfaceProvider = previewView.surfaceProvider

                val analyzer = QrCodeAnalyzer(
                    previewView = previewView,
                    scanFrameFraction = SCAN_FRAME_RATIO,
                    isPaused = { isScanPaused },
                    onResult = { text ->
                        isScanPaused = true
                        handleScanResult(text, context, onOpenUrl, onBack, { resumeScan() }) { msg -> snackbar.show(msg) }
                    },
                )
                @Suppress("DEPRECATION")
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    provider.unbindAll()
                    val cam = provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                    )
                    camera = cam
                } catch (e: Exception) {
                    Log.e(TAG, "Camera bind failed", e)
                    snackbar.show(resources.getString(R.string.qrcode_camera_failed))
                }
            }, executor)

            onDispose {
                // ★ P1 fix: 使用缓存的 provider 实例，不会阻塞主线程
                cameraProvider?.unbindAll()
            }
        }
    }

    // ★ P0 fix: TouchListener 放入 DisposableEffect 中
    DisposableEffect(previewView, camera) {
        val listener = android.view.View.OnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val focusAction = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(focusAction)
            }
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
            }
            true
        }
        previewView.setOnTouchListener(listener)
        onDispose { previewView.setOnTouchListener(null) }
    }

    // ── UI ──
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = {
                        camera?.cameraControl?.enableTorch(false)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White,
                        )
                    }
                },
                // TopAppBar 半透明浮在预览上方
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                ),
            )
        },
        // Scaffold 不应用内边距，实现全屏预览
        modifier = Modifier.fillMaxSize(),
    ) { _ ->
        // ★ 外层 Box 全屏，不应用 padding，预览铺满整个画面
        Box(modifier = Modifier.fillMaxSize()) {
            when (permissionState) {
                is CameraPermissionState.Granted -> {
                    // 相机预览（全屏）
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize(),
                    )

                    // 扫码框遮罩（4-Rect 拼接，全屏）
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height
                            val frameSize = minOf(canvasWidth, canvasHeight) * SCAN_FRAME_RATIO
                            val left = (canvasWidth - frameSize) / 2
                            val top = (canvasHeight - frameSize) / 2
                            val maskColor = Color.Black.copy(alpha = 0.3f)

                            drawRect(maskColor, size = Size(canvasWidth, top))
                            drawRect(maskColor, topLeft = Offset(0f, top + frameSize), size = Size(canvasWidth, canvasHeight - top - frameSize))
                            drawRect(maskColor, topLeft = Offset(0f, top), size = Size(left, frameSize))
                            drawRect(maskColor, topLeft = Offset(left + frameSize, top), size = Size(canvasWidth - left - frameSize, frameSize))

                            val cornerLen = frameSize * 0.08f
                            val strokeWidth = 4.dp.toPx()
                            val cornerColor = Color.White

                            drawLine(cornerColor, Offset(left, top + cornerLen), Offset(left, top), strokeWidth)
                            drawLine(cornerColor, Offset(left, top), Offset(left + cornerLen, top), strokeWidth)
                            drawLine(cornerColor, Offset(left + frameSize - cornerLen, top), Offset(left + frameSize, top), strokeWidth)
                            drawLine(cornerColor, Offset(left + frameSize, top), Offset(left + frameSize, top + cornerLen), strokeWidth)
                            drawLine(cornerColor, Offset(left, top + frameSize - cornerLen), Offset(left, top + frameSize), strokeWidth)
                            drawLine(cornerColor, Offset(left, top + frameSize), Offset(left + cornerLen, top + frameSize), strokeWidth)
                            drawLine(cornerColor, Offset(left + frameSize - cornerLen, top + frameSize), Offset(left + frameSize, top + frameSize), strokeWidth)
                            drawLine(cornerColor, Offset(left + frameSize, top + frameSize), Offset(left + frameSize, top + frameSize - cornerLen), strokeWidth)
                        }
                    }

                    // 闪光灯位于左下角
                    IconButton(
                        onClick = {
                            val newState = !isTorchOn
                            camera?.cameraControl?.enableTorch(newState)
                            isTorchOn = newState
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 24.dp, bottom = 32.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashlightOn
                            else Icons.Default.FlashlightOff,
                            contentDescription = if (isTorchOn) stringResource(R.string.scan_torch_on) else stringResource(R.string.scan_torch_off),
                            tint = if (isTorchOn) Color.Yellow else Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    // 选择图片位于右下角
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 32.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = stringResource(R.string.scan_album),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                is CameraPermissionState.Unknown -> {
                    // 不显示 UI，LaunchedEffect 会自动触发权限请求
                }

                is CameraPermissionState.Denied -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.scan_need_permission),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        androidx.compose.material3.Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text(stringResource(R.string.scan_reauthorize))
                        }
                    }
                }

                is CameraPermissionState.PermanentlyDenied -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.scan_permission_denied),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        androidx.compose.material3.Button(
                            onClick = {
                                val intent = Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text(stringResource(R.string.scan_go_to_settings))
                        }
                    }
                }
            }
        }
    }
}

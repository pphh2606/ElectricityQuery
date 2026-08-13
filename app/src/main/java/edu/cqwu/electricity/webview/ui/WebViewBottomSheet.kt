package edu.cqwu.electricity.webview.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.VelocityTracker
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import edu.cqwu.electricity.R
import edu.cqwu.electricity.login.data.UserAgentProvider
import edu.cqwu.electricity.theme.ui.LocalSheetVisibilityState
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.theme.ui.WebViewErrorOverlay
import edu.cqwu.electricity.theme.util.ToastUtils
import edu.cqwu.electricity.webview.util.WebViewUrlUtil
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "WebViewBottomSheet"

/**
 * 半屏 WebView 弹窗，基于 Material3 ModalBottomSheet 实现。
 *
 * 与普通带拖拽手柄弹窗使用同一套窗口、scrim、模糊与返回键机制，
 * 背景模糊/压暗由 AppShell + SheetVisibilityState 统一驱动。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    url: String,
    title: String = "",
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val sheetVisibilityState = LocalSheetVisibilityState.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }

    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(10) }
    var pageTitle by remember { mutableStateOf(title.ifBlank { resources.getString(R.string.webview_loading) }) }
    var showMenu by remember { mutableStateOf(false) }
    data class WebViewErrorState(val errorCode: Int, val description: String, val isHttpError: Boolean = false)
    var webErrorState by remember { mutableStateOf<WebViewErrorState?>(null) }
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileUploadCallback?.let { cb -> cb.onReceiveValue(if (uri != null) arrayOf(uri) else null); fileUploadCallback = null }
    }
    data class WebViewTouchState(
        var lastRawY: Float = 0f,
        var trackingDown: Boolean = false,
        var downTime: Long = 0L,
        var handedToSheet: Boolean = false,
        var sheetDragDelta: Float = 0f,
        var velocityTracker: VelocityTracker? = null,
    )
    val webViewTouchState = remember { WebViewTouchState() }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
    val webViewNestedScrollConnection = remember { object : NestedScrollConnection {} }

    var isHiding by remember { mutableStateOf(false) }
    var previousVisible by remember { mutableStateOf(visible) }

    if (previousVisible && !visible && !isHiding) {
        isHiding = true
    }
    if (visible && isHiding) {
        isHiding = false
    }
    previousVisible = visible

    LaunchedEffect(isHiding) {
        if (isHiding) {
            webViewRef.value?.stopLoading()
            try {
                if (sheetState.isVisible) {
                    sheetState.hide()
                }
            } finally {
                isHiding = false
                onDismissRequest()
            }
        }
    }

    val isKeyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(isKeyboardVisible) {
        if (isKeyboardVisible && !isHiding && sheetState.currentValue == SheetValue.PartiallyExpanded) {
            sheetState.expand()
        }
    }

    DisposableEffect(visible, isHiding, sheetVisibilityState) {
        val shouldBeOpen = visible || isHiding
        if (shouldBeOpen) {
            sheetVisibilityState.open()
        }
        onDispose {
            if (shouldBeOpen) {
                sheetVisibilityState.close()
            }
        }
    }

    LaunchedEffect(sheetState, sheetVisibilityState) {
        snapshotFlow { sheetState.targetValue == SheetValue.Hidden }
            .distinctUntilChanged()
            .collect { isHiddenTarget ->
                sheetVisibilityState.blurProgress = if (isHiddenTarget) 0f else 1f
            }
    }

    BackHandler(enabled = visible && !isHiding) {
        if (canGoBack) {
            webViewRef.value?.goBack()
        } else {
            onDismissRequest()
        }
    }

    if (visible || isHiding) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            sheetGesturesEnabled = true,
            dragHandle = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        IconButton(
                            onClick = { webViewRef.value?.goBack() },
                            enabled = canGoBack,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                stringResource(R.string.common_back),
                                tint = if (canGoBack) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                },
                            )
                        }
                    }
                    Box(contentAlignment = Alignment.Center) {
                        BottomSheetDefaults.DragHandle()
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                stringResource(R.string.common_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_close)) },
                                leadingIcon = { Icon(Icons.Outlined.Close, null) },
                                onClick = { showMenu = false; onDismissRequest() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.common_refresh)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = { showMenu = false; webViewRef.value?.reload() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.webview_open_in_browser)) },
                                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) },
                                onClick = {
                                    showMenu = false
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(webViewRef.value?.url ?: url))
                                        )
                                    } catch (_: ActivityNotFoundException) {
                                        snackbar.show(
                                            resources.getString(R.string.common_no_browser),
                                            ToastUtils.Type.ERROR,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            contentWindowInsets = { WindowInsets.systemBars.union(WindowInsets.ime) },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.7f),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(
                            connection = webViewNestedScrollConnection,
                            dispatcher = nestedScrollDispatcher,
                        ),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                settings.javaScriptEnabled = true
                                settings.javaScriptCanOpenWindowsAutomatically = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.userAgentString = UserAgentProvider.getActiveUserAgent()

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, pageUrl, favicon)
                                        webErrorState = null; canGoBack = view?.canGoBack() == true
                                    }
                                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                        super.onPageFinished(view, pageUrl)
                                        canGoBack = view?.canGoBack() == true
                                    }
                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?,
                                    ) {
                                        super.onReceivedError(view, request, error)
                                        if (request?.isForMainFrame == true && webErrorState == null) {
                                            val code =
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    error?.errorCode ?: -1
                                                } else {
                                                    -1
                                                }
                                            val description =
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    error?.description?.toString()
                                                } else {
                                                    null
                                                } ?: ctx.getString(R.string.common_unknown_error)
                                            webErrorState = WebViewErrorState(code, description)
                                        }
                                    }
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val pageUrl = request?.url?.toString() ?: return false
                                        return view?.context != null &&
                                            WebViewUrlUtil.openCustomSchemeUrl(view.context, pageUrl, TAG)
                                    }
                                    override fun doUpdateVisitedHistory(
                                        view: WebView?,
                                        pageUrl: String?,
                                        isReload: Boolean,
                                    ) {
                                        super.doUpdateVisitedHistory(view, pageUrl, isReload)
                                        canGoBack = view?.canGoBack() == true
                                    }
                                    @Deprecated("Deprecated in Java")
                                    @Suppress("DEPRECATION")
                                    override fun shouldOverrideUrlLoading(view: WebView?, pageUrl: String?): Boolean {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) return false
                                        return pageUrl != null &&
                                            view?.context != null &&
                                            WebViewUrlUtil.openCustomSchemeUrl(view.context, pageUrl, TAG)
                                    }
                                }

                                setDownloadListener { downloadUrl, _, _, _, _ ->
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                                    } catch (_: ActivityNotFoundException) {
                                        snackbar.show(
                                            ctx.getString(R.string.webview_no_download_tool),
                                            ToastUtils.Type.ERROR,
                                        )
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                    }
                                    override fun onReceivedTitle(view: WebView?, t: String?) {
                                        super.onReceivedTitle(view, t)
                                        if (!t.isNullOrBlank()) pageTitle = t
                                    }
                                    override fun onShowFileChooser(
                                        wv: WebView?,
                                        filePathCallback: ValueCallback<Array<Uri>>?,
                                        params: FileChooserParams?,
                                    ): Boolean {
                                        fileUploadCallback = filePathCallback
                                        fileUploadLauncher.launch("*/*")
                                        return true
                                    }
                                }

                                webViewRef.value = this
                                val headers = HashMap<String, String>()
                                headers["Referer"] =
                                    "https://${try { Uri.parse(url).host } catch (_: Exception) { "" }}/"
                                loadUrl(url, headers)
                            }
                        },
                        update = { webView ->
                            fun VelocityTracker?.addRawMovement(event: MotionEvent) {
                                this ?: return
                                val rawEvent = MotionEvent.obtain(event)
                                rawEvent.offsetLocation(0f, event.rawY - event.y)
                                addMovement(rawEvent)
                                rawEvent.recycle()
                            }
                            webView.setOnTouchListener { _, event ->
                                when (event.actionMasked) {
                                    android.view.MotionEvent.ACTION_DOWN -> {
                                        // Keep the sheet from stealing MOVE events before WebView gets them.
                                        webView.requestDisallowInterceptTouchEvent(true)
                                        webViewTouchState.velocityTracker?.recycle()
                                        webViewTouchState.velocityTracker =
                                            VelocityTracker.obtain().apply { addRawMovement(event) }
                                        webViewTouchState.lastRawY = event.rawY
                                        webViewTouchState.trackingDown = false
                                        webViewTouchState.downTime = event.downTime
                                        webViewTouchState.handedToSheet = false
                                        webViewTouchState.sheetDragDelta = 0f
                                        false
                                    }
                                    android.view.MotionEvent.ACTION_MOVE -> {
                                        webViewTouchState.velocityTracker?.addRawMovement(event)
                                        val dy = event.rawY - webViewTouchState.lastRawY
                                        webViewTouchState.lastRawY = event.rawY
                                        if (webViewTouchState.trackingDown) {
                                            val moveDelta =
                                                if (dy < 0f) {
                                                    maxOf(dy, -webViewTouchState.sheetDragDelta)
                                                } else {
                                                    dy
                                                }
                                            if (moveDelta != 0f) {
                                                val parentsConsumed =
                                                    nestedScrollDispatcher.dispatchPreScroll(
                                                        available = Offset(0f, moveDelta),
                                                        source = NestedScrollSource.UserInput,
                                                    )
                                                val left = moveDelta - parentsConsumed.y
                                                if (left != 0f) {
                                                    nestedScrollDispatcher.dispatchPostScroll(
                                                        consumed = parentsConsumed,
                                                        available = Offset(0f, left),
                                                        source = NestedScrollSource.UserInput,
                                                    )
                                                }
                                                webViewTouchState.sheetDragDelta += moveDelta
                                                if (dy < 0f && webViewTouchState.sheetDragDelta <= 0f) {
                                                    webViewTouchState.trackingDown = false
                                                }
                                                true
                                            } else {
                                                if (dy < 0f) {
                                                    webViewTouchState.trackingDown = false
                                                    false
                                                } else {
                                                    true
                                                }
                                            }
                                        } else if (
                                            webView.scrollY == 0 &&
                                            dy > 0f &&
                                            !webView.canScrollVertically(-1)
                                        ) {
                                            // At the page top, hand downward drags to the sheet.
                                            webViewTouchState.trackingDown = true
                                            webViewTouchState.sheetDragDelta = dy
                                            if (!webViewTouchState.handedToSheet) {
                                                webViewTouchState.handedToSheet = true
                                                webView.cancelLongPress()
                                                val downTime = event.downTime
                                                val x = event.x
                                                val y = event.y
                                                webView.post {
                                                    if (webViewTouchState.downTime == downTime) {
                                                        val cancelEvent = MotionEvent.obtain(
                                                            downTime,
                                                            SystemClock.uptimeMillis(),
                                                            MotionEvent.ACTION_CANCEL,
                                                            x,
                                                            y,
                                                            0,
                                                        )
                                                        webView.onTouchEvent(cancelEvent)
                                                        cancelEvent.recycle()
                                                    }
                                                }
                                            }
                                            val parentsConsumed = nestedScrollDispatcher.dispatchPreScroll(
                                                available = Offset(0f, dy),
                                                source = NestedScrollSource.UserInput,
                                            )
                                            val left = dy - parentsConsumed.y
                                            if (left > 0f) {
                                                nestedScrollDispatcher.dispatchPostScroll(
                                                    consumed = parentsConsumed,
                                                    available = Offset(0f, left),
                                                    source = NestedScrollSource.UserInput,
                                                )
                                            }
                                            true
                                        } else {
                                            webViewTouchState.trackingDown = false
                                            false
                                        }
                                    }
                                    android.view.MotionEvent.ACTION_UP -> {
                                        webViewTouchState.velocityTracker?.addRawMovement(event)
                                        webViewTouchState.velocityTracker?.computeCurrentVelocity(1000)
                                        val velocity = webViewTouchState.velocityTracker?.yVelocity ?: 0f
                                        webViewTouchState.velocityTracker?.recycle()
                                        webViewTouchState.velocityTracker = null
                                        webView.requestDisallowInterceptTouchEvent(false)
                                        if (webViewTouchState.trackingDown) {
                                            scope.launch {
                                                nestedScrollDispatcher.dispatchPostFling(
                                                    consumed = Velocity.Zero,
                                                    available = Velocity(0f, velocity),
                                                )
                                            }
                                            webViewTouchState.trackingDown = false
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    android.view.MotionEvent.ACTION_CANCEL -> {
                                        webViewTouchState.velocityTracker?.recycle()
                                        webViewTouchState.velocityTracker = null
                                        webView.requestDisallowInterceptTouchEvent(false)
                                        webViewTouchState.trackingDown = false
                                        false
                                    }
                                    else -> false
                                }
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading(); webView.loadUrl("about:blank")
                            webView.clearHistory(); webView.removeAllViews(); webView.destroy()
                        },
                    )

                    if (progress < 100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }

                    webErrorState?.let { error ->
                        WebViewErrorOverlay(
                            errorCode = error.errorCode,
                            description = error.description,
                            isHttpError = error.isHttpError,
                            onRetry = { webErrorState = null; webViewRef.value?.reload() },
                        )
                    }
                }
            }
        }
    }
}

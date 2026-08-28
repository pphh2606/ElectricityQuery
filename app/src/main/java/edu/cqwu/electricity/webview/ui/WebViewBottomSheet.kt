package edu.cqwu.electricity.webview.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import android.webkit.ValueCallback
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import edu.cqwu.electricity.common.ui.AppScaledDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import edu.cqwu.electricity.R
import edu.cqwu.electricity.app.Routes
import edu.cqwu.electricity.common.ui.BottomSheetDialog
import edu.cqwu.electricity.theme.ui.LocalNavController
import edu.cqwu.electricity.theme.ui.LocalWebViewReloadAfterLogin
import edu.cqwu.electricity.theme.ui.LocalWebViewReloadConsumed
import edu.cqwu.electricity.theme.ui.LocalSnackbarController
import edu.cqwu.electricity.common.ui.ReLoginContent
import edu.cqwu.electricity.common.ui.WebViewErrorOverlay
import edu.cqwu.electricity.theme.util.ToastUtils
import edu.cqwu.electricity.webview.util.WebViewUrlUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private data class WebViewErrorState(
    val errorCode: Int,
    val description: String,
    val isHttpError: Boolean = false,
)

private class WebViewTouchState {
    var lastRawY: Float = 0f
    var trackingDown: Boolean = false
    var downTime: Long = 0L
    var handedToSheet: Boolean = false
    var sheetDragDelta: Float = 0f
    var velocityTracker: VelocityTracker? = null
}

private fun VelocityTracker?.addRawMovement(event: MotionEvent) {
    this ?: return
    val rawEvent = MotionEvent.obtain(event)
    rawEvent.offsetLocation(0f, event.rawY - event.y)
    addMovement(rawEvent)
    rawEvent.recycle()
}

private class WebViewBottomSheetTouchHandler(
    private val scope: CoroutineScope,
    private val dispatcher: NestedScrollDispatcher,
) {
    private val state = WebViewTouchState()

    fun onTouch(webView: WebView, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                webView.requestDisallowInterceptTouchEvent(true)
                state.velocityTracker?.recycle()
                state.velocityTracker = VelocityTracker.obtain().apply { addRawMovement(event) }
                state.lastRawY = event.rawY
                state.trackingDown = false
                state.downTime = event.downTime
                state.handedToSheet = false
                state.sheetDragDelta = 0f
                false
            }
            MotionEvent.ACTION_MOVE -> {
                state.velocityTracker?.addRawMovement(event)
                val dy = event.rawY - state.lastRawY
                state.lastRawY = event.rawY
                if (state.trackingDown) {
                    val moveDelta =
                        if (dy < 0f) {
                            maxOf(dy, -state.sheetDragDelta)
                        } else {
                            dy
                        }
                    if (moveDelta != 0f) {
                        val parentsConsumed = dispatcher.dispatchPreScroll(
                            available = Offset(0f, moveDelta),
                            source = NestedScrollSource.UserInput,
                        )
                        val left = moveDelta - parentsConsumed.y
                        if (left != 0f) {
                            dispatcher.dispatchPostScroll(
                                consumed = parentsConsumed,
                                available = Offset(0f, left),
                                source = NestedScrollSource.UserInput,
                            )
                        }
                        state.sheetDragDelta += moveDelta
                        if (dy < 0f && state.sheetDragDelta <= 0f) {
                            state.trackingDown = false
                        }
                        true
                    } else {
                        if (dy < 0f) {
                            state.trackingDown = false
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
                    state.trackingDown = true
                    state.sheetDragDelta = dy
                    if (!state.handedToSheet) {
                        state.handedToSheet = true
                        webView.cancelLongPress()
                        val downTime = event.downTime
                        val x = event.x
                        val y = event.y
                        webView.post {
                            if (state.downTime == downTime) {
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
                    val parentsConsumed = dispatcher.dispatchPreScroll(
                        available = Offset(0f, dy),
                        source = NestedScrollSource.UserInput,
                    )
                    val left = dy - parentsConsumed.y
                    if (left > 0f) {
                        dispatcher.dispatchPostScroll(
                            consumed = parentsConsumed,
                            available = Offset(0f, left),
                            source = NestedScrollSource.UserInput,
                        )
                    }
                    true
                } else {
                    state.trackingDown = false
                    false
                }
            }
            MotionEvent.ACTION_UP -> {
                state.velocityTracker?.addRawMovement(event)
                state.velocityTracker?.computeCurrentVelocity(1000)
                val velocity = state.velocityTracker?.yVelocity ?: 0f
                state.velocityTracker?.recycle()
                state.velocityTracker = null
                webView.requestDisallowInterceptTouchEvent(false)
                if (state.trackingDown) {
                    scope.launch {
                        dispatcher.dispatchPostFling(
                            consumed = Velocity.Zero,
                            available = Velocity(0f, velocity),
                        )
                    }
                    state.trackingDown = false
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                state.velocityTracker?.recycle()
                state.velocityTracker = null
                webView.requestDisallowInterceptTouchEvent(false)
                state.trackingDown = false
                false
            }
            else -> false
        }
    }
}

/**
 * Half-screen WebView dialog built on BottomSheetDialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    url: String,
    @Suppress("UNUSED_PARAMETER")
    title: String = "",
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalSnackbarController.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val screenHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val webViewState = rememberWebViewHostState()
    val nav = LocalNavController.current
    val reloadAfterLogin = LocalWebViewReloadAfterLogin.current
    val onReloadConsumed = LocalWebViewReloadConsumed.current

    var showMenu by remember { mutableStateOf(false) }
    var webErrorState by remember { mutableStateOf<WebViewErrorState?>(null) }
    var loginRequiredOverlayVisible by remember { mutableStateOf(false) }
    var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileUploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileUploadCallback?.let { cb ->
            cb.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            fileUploadCallback = null
        }
    }

    val nestedScrollDispatcher = remember { NestedScrollDispatcher() }
    val webViewNestedScrollConnection = remember { object : NestedScrollConnection {} }
    val touchHandler = remember { WebViewBottomSheetTouchHandler(scope, nestedScrollDispatcher) }

    LaunchedEffect(reloadAfterLogin) {
        if (reloadAfterLogin) {
            onReloadConsumed()
            webViewState.reload()
        }
    }

    BackHandler(enabled = visible) {
        if (webViewState.canGoBack) {
            webViewState.goBack()
        } else {
            onDismissRequest()
        }
    }

    BottomSheetDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        onHideStarted = { webViewState.stopLoading() },
        leadingButton = {
            IconButton(
                onClick = { webViewState.goBack() },
                enabled = webViewState.canGoBack,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    stringResource(R.string.common_back),
                    tint = if (webViewState.canGoBack) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    },
                )
            }
        },
        trailingButton = {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    Icons.Outlined.MoreVert,
                    stringResource(R.string.common_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppScaledDropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_close)) },
                    leadingIcon = { Icon(Icons.Outlined.Close, null) },
                    onClick = { showMenu = false; onDismissRequest() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_refresh)) },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                    onClick = { showMenu = false; webViewState.reload() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.webview_open_in_browser)) },
                    leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) },
                    onClick = {
                        showMenu = false
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(webViewState.webView?.url ?: url))
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
        },
        contentPadding = PaddingValues(0.dp),
        contentArrangement = Arrangement.Top,
        contentModifier = Modifier.height(screenHeight * 0.7f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(
                    connection = webViewNestedScrollConnection,
                    dispatcher = nestedScrollDispatcher,
                ),
        ) {
            WebViewHost(
                state = webViewState,
                modifier = Modifier.fillMaxSize(),
                enableZoom = true,
                showZoomControls = false,
                onBeforeLoad = { webView ->
                    val headers = HashMap<String, String>()
                    headers["Referer"] =
                        "https://${try { Uri.parse(url).host } catch (_: Exception) { "" }}/"
                    webView.loadUrl(url, headers)
                },
                onPageStarted = { _, _ ->
                    webErrorState = null
                    loginRequiredOverlayVisible = false
                },
                onPageFinished = { _, pageUrl ->
                    loginRequiredOverlayVisible = WebViewUrlUtil.shouldShowLoginRequired(pageUrl, webErrorState != null)
                },
                onMainFrameError = { _, error ->
                    loginRequiredOverlayVisible = false
                    webErrorState = WebViewErrorState(
                        errorCode = error.errorCode,
                        description = error.description ?: context.getString(R.string.common_unknown_error),
                    )
                },
                onFileChooser = { callback ->
                    fileUploadCallback = callback
                    fileUploadLauncher.launch("*/*")
                    true
                },
                onDownload = { downloadUrl ->
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                    } catch (_: ActivityNotFoundException) {
                        snackbar.show(
                            context.getString(R.string.webview_no_download_tool),
                            ToastUtils.Type.ERROR,
                        )
                    }
                },
                update = { webView ->
                    webView.setOnTouchListener { _, event ->
                        touchHandler.onTouch(webView, event)
                    }
                },
            )

            WebViewProgress(
                progress = webViewState.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )

            webErrorState?.let { error ->
                WebViewErrorOverlay(
                    errorCode = error.errorCode,
                    description = error.description,
                    isHttpError = error.isHttpError,
                    onRetry = { webErrorState = null; webViewState.reload() },
                )
            }

            if (loginRequiredOverlayVisible) {
                ReLoginContent(
                    requiresReLogin = true,
                    onReLogin = { nav.navigate(Routes.WEBVIEW_LOGIN) },
                    consumeTouches = true,
                )
            }
        }
    }
}

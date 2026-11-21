package com.hpprint.app.ui.screen.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hpprint.app.R
import com.hpprint.app.data.scan.camera.ScanQuality
import com.hpprint.app.data.scan.camera.ScanTier
import com.hpprint.app.data.scan.core.BitmapUtil
import com.hpprint.app.data.scan.core.DocDetector
import com.hpprint.app.data.scan.core.NativeScanEngine
import com.hpprint.app.data.scan.core.Perspective
import com.hpprint.app.data.scan.core.Quad
import com.hpprint.app.ui.util.rememberEdgeHaptic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

private class CaptureCrop(
    val bitmap: Bitmap,
    val quad: Quad?,
)

/**
 * 全屏取景拍摄：实时检测边缘并画出校准框，拍摄后可四角微调，确认时透视拉正。
 * 结果通过 [onCaptured] 返回，供身份证复印、证件打印等复用。
 */
@Composable
fun CardCameraCapture(
    title: String,
    onCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    guideAspect: Float = ID_CARD_ASPECT,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            CardCameraContent(title, onCaptured, onCancel, guideAspect)
        }
    }
}

@Composable
private fun CardCameraContent(
    title: String,
    onCaptured: (Bitmap) -> Unit,
    onCancel: () -> Unit,
    guideAspect: Float,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (!ok) {
            Toast.makeText(context, context.getString(R.string.hp_scan_camera_permission), Toast.LENGTH_LONG).show()
            onCancel()
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var overlayQuad by remember { mutableStateOf<Quad?>(null) }
    var frameSize by remember { mutableStateOf(IntArray(2)) }
    var crop by remember { mutableStateOf<CaptureCrop?>(null) }
    var cropViewRef by remember { mutableStateOf<QuadCropView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var autoCrop by remember { mutableStateOf(true) }
    var flashOn by remember { mutableStateOf(false) }
    var tier by remember { mutableStateOf(ScanQuality.load(context)) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val busyFlag = remember { AtomicBoolean(false) }
    val lastAnalysis = remember { longArrayOf(0L) }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    LaunchedEffect(granted, tier) {
        if (!granted) return@LaunchedEffect
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build()
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                ScanQuality.sizeFor(context, tier)?.let { captureBuilder.setMaxResolution(it) }
                val capture = captureBuilder.build()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(1280, 720))
                    .build()
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    try {
                        if (!autoCrop || busyFlag.get()) return@setAnalyzer
                        val now = System.currentTimeMillis()
                        if (now - lastAnalysis[0] < 260L) return@setAnalyzer
                        lastAnalysis[0] = now
                        val upright = analysisFrameToBitmap(proxy) ?: return@setAnalyzer
                        // 原生扫描模型优先；不可用时回退现有 Kotlin 检测
                        val quad = if (NativeScanEngine.available) {
                            NativeScanEngine.detectBitmap(upright)?.let { c -> Quad(c[0], c[1], c[2], c[3]) }
                        } else {
                            DocDetector.detect(upright)
                        }
                        val size = intArrayOf(upright.width, upright.height)
                        upright.recycle()
                        context.mainExecutor.execute {
                            frameSize = size
                            overlayQuad = quad
                        }
                    } catch (_: Throwable) {
                    } finally {
                        proxy.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis,
                )
                imageCapture = capture
                previewUseCase = preview
                capture.flashMode =
                    if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val bmp = withContext(Dispatchers.IO) { BitmapUtil.decodeUri(context, uri) }
            if (bmp == null) {
                busy = false
                context.mainExecutor.execute {
                    Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            val quad = withContext(Dispatchers.IO) {
                if (autoCrop) DocDetector.detect(bmp) else null
            }
            busy = false
            crop = CaptureCrop(bmp, quad)
        }
    }

    fun shoot() {
        val capture = imageCapture ?: return
        if (busy || crop != null) return
        busy = true
        busyFlag.set(true)
        overlayQuad = null
        capture.takePicture(captureExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = try {
                    captureJpegToBitmap(image)
                } catch (_: Throwable) {
                    null
                } finally {
                    image.close()
                }
                if (bmp == null) {
                    busy = false
                    busyFlag.set(false)
                    context.mainExecutor.execute {
                        Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                val quad = if (autoCrop) DocDetector.detect(bmp) else null
                context.mainExecutor.execute {
                    busy = false
                    busyFlag.set(false)
                    crop = CaptureCrop(bmp, quad)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                busy = false
                busyFlag.set(false)
                context.mainExecutor.execute {
                    Toast.makeText(context, context.getString(R.string.hp_scan_failed), Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    fun confirm() {
        val state = crop ?: return
        val quad = cropViewRef?.getQuad() ?: state.quad
            ?: Quad.insetRect(state.bitmap.width.toFloat(), state.bitmap.height.toFloat())
        busy = true
        scope.launch {
            val warped = withContext(Dispatchers.IO) {
                Perspective.warpBitmap(state.bitmap, quad, 2600)
            }
            busy = false
            crop = null
            onCaptured(warped)
        }
    }

    val currentCrop = crop
    if (currentCrop != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            CropTopBar(
                title = stringResource(R.string.hp_scan_confirm),
                onBack = { crop = null },
            )
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    QuadCropView(ctx).also { v ->
                        v.setImage(currentCrop.bitmap, currentCrop.quad)
                        cropViewRef = v
                    }
                },
                update = { v ->
                    if (v.getBitmap() !== currentCrop.bitmap) v.setImage(currentCrop.bitmap, currentCrop.quad)
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapsuleButton(stringResource(R.string.hp_scan_rotate)) { cropViewRef?.rotate90() }
                CapsuleButton(stringResource(R.string.hp_scan_reset)) { cropViewRef?.resetQuad() }
                CapsuleButton(stringResource(R.string.hp_scan_retake)) { crop = null }
                CapsuleButtonPrimary(stringResource(R.string.hp_scan_confirm)) { confirm() }
            }
            Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        CropTopBar(
            title = title,
            onBack = onCancel,
            trailing = {
                IconPill(
                    icon = if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = stringResource(
                        if (flashOn) R.string.hp_scan_flash_on else R.string.hp_scan_flash_off
                    ),
                    onClick = {
                        flashOn = !flashOn
                        imageCapture?.flashMode =
                            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                    },
                )
            },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (granted) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }.also { pv -> previewUseCase?.setSurfaceProvider(pv.surfaceProvider) }
                    },
                    update = { pv -> previewUseCase?.setSurfaceProvider(pv.surfaceProvider) },
                )
            }
            val quad = overlayQuad
            val fw = frameSize[0]
            val fh = frameSize[1]
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 证件居中取景引导框：按所选证件类型的宽高比，略高于画面中心
                val frameWidth = min(size.width * 0.8f, size.height * 0.55f * guideAspect)
                val frameHeight = frameWidth / guideAspect
                val centerX = size.width / 2f
                val centerY = size.height * 0.46f
                val frame = Rect(
                    centerX - frameWidth / 2f,
                    centerY - frameHeight / 2f,
                    centerX + frameWidth / 2f,
                    centerY + frameHeight / 2f,
                )
                val corner = CornerRadius(18.dp.toPx())
                val mask = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(frame, corner))
                }
                drawPath(mask, Color.Black.copy(alpha = 0.45f))
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(frame.left, frame.top),
                    size = androidx.compose.ui.geometry.Size(frame.width, frame.height),
                    cornerRadius = corner,
                    style = Stroke(2.dp.toPx()),
                )
                // 四角加粗括号
                val len = 26.dp.toPx()
                val sw = 3.5.dp.toPx()
                val bracket = Color.White.copy(alpha = 0.9f)
                drawLine(bracket, Offset(frame.left, frame.top + len), Offset(frame.left, frame.top), sw)
                drawLine(bracket, Offset(frame.left, frame.top), Offset(frame.left + len, frame.top), sw)
                drawLine(bracket, Offset(frame.right - len, frame.top), Offset(frame.right, frame.top), sw)
                drawLine(bracket, Offset(frame.right, frame.top), Offset(frame.right, frame.top + len), sw)
                drawLine(bracket, Offset(frame.right, frame.bottom - len), Offset(frame.right, frame.bottom), sw)
                drawLine(bracket, Offset(frame.right, frame.bottom), Offset(frame.right - len, frame.bottom), sw)
                drawLine(bracket, Offset(frame.left + len, frame.bottom), Offset(frame.left, frame.bottom), sw)
                drawLine(bracket, Offset(frame.left, frame.bottom), Offset(frame.left, frame.bottom - len), sw)

                if (quad != null && fw > 0 && fh > 0) {
                    val scale = max(size.width / fw, size.height / fh)
                    val dx = (size.width - fw * scale) / 2f
                    val dy = (size.height - fh * scale) / 2f
                    val pts = quad.points().map { Offset(it.x * scale + dx, it.y * scale + dy) }
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        close()
                    }
                    drawPath(path, Color(0xFFFABB06).copy(alpha = 0.25f), style = Stroke(6.dp.toPx()))
                    drawPath(path, Color(0xFFFABB06), style = Stroke(2.dp.toPx()))
                    for (p in pts) drawCircle(Color.White, 3.5.dp.toPx(), p)
                }
            }
            CaptureHintRow(
                text = stringResource(
                    if (quad != null && autoCrop) R.string.hp_scan_edge_found
                    else R.string.hp_id_card_frame_hint
                ),
                active = quad != null && autoCrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 38.dp),
            )

            if (busy) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.hp_scanning),
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        CaptureConsole {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScanQualityChip(
                    tier = tier,
                    modifier = Modifier.weight(1f),
                ) { picked ->
                    tier = picked
                    ScanQuality.save(context, picked)
                }

                CaptureSettingChip(
                    text = stringResource(
                        if (autoCrop) R.string.hp_scan_auto_crop_on else R.string.hp_scan_auto_crop_off
                    ),
                    active = autoCrop,
                    modifier = Modifier.weight(1f),
                ) {
                    autoCrop = !autoCrop
                    if (!autoCrop) overlayQuad = null
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp, end = 26.dp, top = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CaptureAlbumButton(
                    label = stringResource(R.string.hp_scan_album_short),
                ) { albumLauncher.launch("image/*") }

                CaptureShutter(busy = busy) { shoot() }

                // 证件单次拍摄，右侧占位保持快门居中
                Spacer(Modifier.width(60.dp))
            }

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

/** 身份证宽高比 85.6 : 54 */
private const val ID_CARD_ASPECT = 85.6f / 54f

@Composable
private fun CropTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp,
                start = 18.dp,
                end = 18.dp,
                bottom = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconPill(icon = Icons.Rounded.ArrowBack, contentDescription = null, onClick = onBack)
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
private fun IconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    val haptic = rememberEdgeHaptic()
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .clickable {
                haptic()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CapsuleButton(text: String, onClick: () -> Unit) {
    val haptic = rememberEdgeHaptic()
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun CapsuleButtonPrimary(text: String, onClick: () -> Unit) {
    val haptic = rememberEdgeHaptic()
    Box(
        modifier = Modifier
            .background(Color(0xFFFABB06), RoundedCornerShape(50))
            .clickable {
                haptic()
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun analysisFrameToBitmap(proxy: ImageProxy): Bitmap? {
    val plane = proxy.planes.getOrNull(0) ?: return null
    val w = proxy.width
    val h = proxy.height
    if (w <= 0 || h <= 0) return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.rewind()
    buffer.get(bytes)

    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    if (rowStride <= 0) return null

    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val rowStart = y * rowStride
        for (x in 0 until w) {
            val idx = rowStart + x * pixelStride
            if (idx >= bytes.size) continue
            val v = bytes[idx].toInt() and 0xFF
            pixels[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)

    val rotation = proxy.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val r = BitmapUtil.rotate(bmp, rotation.toFloat())
        if (r !== bmp) bmp.recycle()
        r
    } else {
        bmp
    }
}

private fun captureJpegToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.rewind()
    buffer.get(bytes)
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

    val exifDegrees = runCatching {
        java.io.ByteArrayInputStream(bytes).use { input ->
            when (ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    }.getOrDefault(0)
    val rotation = if (exifDegrees != 0) exifDegrees else image.imageInfo.rotationDegrees
    if (rotation != 0) {
        val r = BitmapUtil.rotate(bmp, rotation.toFloat())
        if (r !== bmp) bmp.recycle()
        bmp = r
    }

    val long = max(bmp.width, bmp.height)
    val cap = 2600
    if (long > cap) {
        val k = cap.toFloat() / long
        val nb = Bitmap.createScaledBitmap(
            bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true
        )
        if (nb !== bmp) bmp.recycle()
        bmp = nb
    }
    return bmp
}

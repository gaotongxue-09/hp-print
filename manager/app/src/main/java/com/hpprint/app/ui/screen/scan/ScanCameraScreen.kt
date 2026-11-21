package com.hpprint.app.ui.screen.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.util.Size
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hpprint.app.R
import com.hpprint.app.data.scan.camera.ScanQuality
import com.hpprint.app.data.scan.camera.ScanTier
import com.hpprint.app.data.scan.core.BitmapUtil
import com.hpprint.app.data.scan.core.DocDetector
import com.hpprint.app.data.scan.core.DocStore
import com.hpprint.app.data.scan.core.NativeScanEngine
import com.hpprint.app.data.scan.core.Perspective
import com.hpprint.app.data.scan.core.Quad
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private class CropState(
    val bitmap: Bitmap,
    val quad: Quad?,
)

/**
 * 相机文档扫描：实时边缘检测 + 拍摄后四角微调 + 多页追加。
 * 扫描结果写入 [DocStore]，完成后进入多页文档页。
 */
@Composable
fun ScanCameraScreen(initialDocId: String = "") {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val store = remember { DocStore.get(context) }

    var docId by remember { mutableStateOf(initialDocId) }
    var pageCount by remember { mutableIntStateOf(0) }
    var multiMode by remember { mutableStateOf(false) }
    var autoCrop by remember { mutableStateOf(true) }
    var flashOn by remember { mutableStateOf(false) }
    var tier by remember { mutableStateOf(ScanQuality.load(context)) }
    var busy by remember { mutableStateOf(false) }
    var overlayQuad by remember { mutableStateOf<Quad?>(null) }
    var frameSize by remember { mutableStateOf(IntArray(2)) }
    var crop by remember { mutableStateOf<CropState?>(null) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var cropViewRef by remember { mutableStateOf<QuadCropView?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val busyFlag = remember { AtomicBoolean(false) }
    val lastAnalysis = remember { longArrayOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (!ok) {
            Toast.makeText(context, context.getString(R.string.hp_scan_camera_permission), Toast.LENGTH_LONG).show()
            navigator.pop()
        }
    }

    LaunchedEffect(Unit) {
        if (granted) return@LaunchedEffect
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun currentDocOrCreate(): com.hpprint.app.data.scan.core.Doc? {
        if (docId.isNotBlank()) store.findDoc(docId)?.let { return it }
        val doc = store.createDoc()
        docId = doc.id
        return doc
    }

    fun addPage(doc: com.hpprint.app.data.scan.core.Doc, bitmap: Bitmap) {
        store.addPage(doc, bitmap)
        pageCount = doc.pages.size
    }

    fun finishToDoc() {
        val id = docId
        if (id.isBlank()) {
            navigator.pop()
            return
        }
        val below = navigator.backStack.getOrNull(navigator.backStackSize() - 2)
        if (below is Route.ScanDoc && below.docId == id) navigator.pop()
        else navigator.replace(Route.ScanDoc(id))
    }

    fun importFromAlbum(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            val doc = currentDocOrCreate() ?: run { busy = false; return@launch }
            val added = withContext(Dispatchers.IO) {
                var ok = 0
                for (uri in uris) {
                    val bmp = BitmapUtil.decodeUri(context, uri) ?: continue
                    val quad = if (autoCrop) DocDetector.detect(bmp) else null
                    val warped = Perspective.warpBitmap(
                        bmp, quad ?: Quad.insetRect(bmp.width.toFloat(), bmp.height.toFloat())
                    )
                    store.addPage(doc, warped)
                    if (warped !== bmp) warped.recycle()
                    bmp.recycle()
                    ok++
                }
                ok
            }
            pageCount = doc.pages.size
            busy = false
            if (added > 0) {
                Toast.makeText(context, context.getString(R.string.hp_scan_imported, added), Toast.LENGTH_SHORT).show()
                if (!multiMode) finishToDoc()
            }
        }
    }

    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (!uris.isNullOrEmpty()) importFromAlbum(uris) }

    // 相机绑定
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
                        val upright = yPlaneToBitmap(proxy) ?: return@setAnalyzer
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

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
            analysisExecutor.shutdown()
            captureExecutor.shutdown()
        }
    }

    LaunchedEffect(pageCount) {
        if (initialDocId.isNotBlank() && pageCount == 0) {
            pageCount = store.findDoc(initialDocId)?.pages?.size ?: 0
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
                    jpegToBitmap(image)
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
                    crop = CropState(bmp, quad)
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

    fun confirmCrop() {
        val state = crop ?: return
        val doc = currentDocOrCreate() ?: return
        val view = cropViewRef
        val quad = view?.getQuad() ?: state.quad
            ?: Quad.insetRect(state.bitmap.width.toFloat(), state.bitmap.height.toFloat())
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val warped = Perspective.warpBitmap(state.bitmap, quad, 2600)
                store.addPage(doc, warped)
                if (warped !== state.bitmap) warped.recycle()
            }
            pageCount = doc.pages.size
            crop = null
            busy = false
            if (!multiMode) finishToDoc()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            crop != null -> {
                val state = crop!!
                Column(modifier = Modifier.fillMaxSize()) {
                    CropTopBar(
                        title = stringResource(R.string.hp_scan_confirm),
                        onBack = {
                            crop = null
                        },
                    )
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { ctx ->
                            QuadCropView(ctx).also { v ->
                                v.setImage(state.bitmap, state.quad)
                                cropViewRef = v
                            }
                        },
                        update = { v ->
                            if (v.getBitmap() !== state.bitmap) v.setImage(state.bitmap, state.quad)
                        },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CapsuleButton(stringResource(R.string.hp_scan_rotate)) {
                            cropViewRef?.rotate90()
                        }
                        CapsuleButton(stringResource(R.string.hp_scan_reset)) {
                            cropViewRef?.resetQuad()
                        }
                        CapsuleButton(stringResource(R.string.hp_scan_retake)) {
                            crop = null
                        }
                        CapsuleButtonPrimary(stringResource(R.string.hp_scan_confirm)) {
                            confirmCrop()
                        }
                    }
                    Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    CropTopBar(
                        title = stringResource(R.string.hp_scan_multi_title),
                        onBack = { navigator.pop() },
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
                        // 实时文档边缘提示
                        val currentQuad = overlayQuad
                        val fw = frameSize[0]
                        val fh = frameSize[1]
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val inset = 20.dp.toPx()
                            // 三分网格 + 四角括号
                            val gridColor = Color.White.copy(alpha = 0.18f)
                            val bracketColor = Color.White.copy(alpha = 0.5f)
                            val left = inset
                            val top = inset
                            val right = size.width - inset
                            val bottom = size.height - inset
                            for (i in 1..2) {
                                val x = left + (right - left) * i / 3f
                                val y = top + (bottom - top) * i / 3f
                                drawLine(gridColor, Offset(x, top), Offset(x, bottom), 1.dp.toPx())
                                drawLine(gridColor, Offset(left, y), Offset(right, y), 1.dp.toPx())
                            }
                            val len = 26.dp.toPx()
                            val sw = 2.dp.toPx()
                            drawLine(bracketColor, Offset(left, top), Offset(left + len, top), sw)
                            drawLine(bracketColor, Offset(left, top), Offset(left, top + len), sw)
                            drawLine(bracketColor, Offset(right - len, top), Offset(right, top), sw)
                            drawLine(bracketColor, Offset(right, top), Offset(right, top + len), sw)
                            drawLine(bracketColor, Offset(right, bottom), Offset(right - len, bottom), sw)
                            drawLine(bracketColor, Offset(right, bottom), Offset(right, bottom - len), sw)
                            drawLine(bracketColor, Offset(left, bottom), Offset(left + len, bottom), sw)
                            drawLine(bracketColor, Offset(left, bottom - len), Offset(left, bottom), sw)

                            if (currentQuad != null && fw > 0 && fh > 0) {
                                val scale = max(size.width / fw, size.height / fh)
                                val dx = (size.width - fw * scale) / 2f
                                val dy = (size.height - fh * scale) / 2f
                                val pts = currentQuad.points().map {
                                    Offset(it.x * scale + dx, it.y * scale + dy)
                                }
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
                                when {
                                    multiMode && pageCount > 0 -> R.string.hp_scan_shoot_hint_multi
                                    overlayQuad != null && autoCrop -> R.string.hp_scan_edge_found
                                    else -> R.string.hp_scan_shoot_hint
                                }
                            ),
                            active = overlayQuad != null && autoCrop,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 38.dp),
                        )
                    }

                    CaptureConsole {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CaptureSettingChip(
                                text = stringResource(
                                    if (multiMode) R.string.hp_scan_mode_multi else R.string.hp_scan_mode_single
                                ),
                                active = multiMode,
                                modifier = Modifier.weight(1f),
                            ) { multiMode = !multiMode }

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
                            ) { albumLauncher.launch(arrayOf("image/*")) }

                            CaptureShutter(busy = busy) { shoot() }

                            CaptureDoneBadge(
                                count = pageCount,
                                label = stringResource(R.string.hp_scan_done),
                            ) { finishToDoc() }
                        }

                        Spacer(
                            Modifier.height(
                                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            )
                        )
                    }
                }
            }
        }

        if (busy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.hp_scan_processing),
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

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
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CapsuleButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun CapsuleButtonPrimary(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Color(0xFFFABB06), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
    ) {
        Text(text, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 只取 Y 通道（本身就是灰度），再按 sensor 方向旋正 */
private fun yPlaneToBitmap(proxy: ImageProxy): Bitmap? {
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
    } else bmp
}

private fun jpegToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.rewind()
    buffer.get(bytes)
    val opts = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

    // JPEG 像素方向由 EXIF 记录；EXIF 缺失时再退回 CameraX 的 rotationDegrees
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

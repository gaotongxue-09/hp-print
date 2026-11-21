package com.hpprint.app.ui.screen.idphoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hpprint.app.BuildConfig
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

data class IdPhotoSpec(val label: String, val wMm: Float, val hMm: Float)
data class IdPhotoLayoutPaper(val label: String, val wMm: Float, val hMm: Float)

object IdPhotoLayout {
    private const val DPI = 300f

    val specs = listOf(
        IdPhotoSpec("一寸 25×35mm", 25f, 35f),
        IdPhotoSpec("小二寸 35×45mm", 35f, 45f),
        IdPhotoSpec("二寸 35×49mm", 35f, 49f),
        IdPhotoSpec("大一寸 33×48mm", 33f, 48f),
    )
    val papers = listOf(
        IdPhotoLayoutPaper("5 寸 89×127mm", 89f, 127f),
        IdPhotoLayoutPaper("6 寸 102×152mm", 102f, 152f),
        IdPhotoLayoutPaper("7 寸 127×178mm", 127f, 178f),
        IdPhotoLayoutPaper("A4 210×297mm", 210f, 297f),
    )

    private fun px(mm: Float): Int = (mm * DPI / 25.4f).roundToInt()

    fun layout(photo: Bitmap, spec: IdPhotoSpec, paper: IdPhotoLayoutPaper, marginMm: Float = 5f): Bitmap {
        val pw = px(paper.wMm)
        val ph = px(paper.hMm)
        val bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val sw = px(spec.wMm)
        val sh = px(spec.hMm)
        val margin = px(marginMm)
        val cols = maxOf(1, (pw - margin) / (sw + margin))
        val rows = maxOf(1, (ph - margin) / (sh + margin))
        val totalW = cols * sw + (cols - 1) * margin
        val totalH = rows * sh + (rows - 1) * margin
        val startX = (pw - totalW) / 2
        val startY = (ph - totalH) / 2
        for (r in 0 until rows) {
            for (col in 0 until cols) {
                val x = startX + col * (sw + margin)
                val y = startY + r * (sh + margin)
                c.drawBitmap(photo, null, RectF(x.toFloat(), y.toFloat(), (x + sw).toFloat(), (y + sh).toFloat()), null)
            }
        }
        return bmp
    }
}

@Composable
fun IdPhotoLayoutScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var specIndex by remember { mutableStateOf(0) }
    var paperIndex by remember { mutableStateOf(1) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
                if (bmp != null) photo = bmp
                else Toast.makeText(context, context.getString(R.string.idphoto_load_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun printResult() {
        val p = photo ?: return
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                IdPhotoLayout.layout(p, IdPhotoLayout.specs[specIndex], IdPhotoLayout.papers[paperIndex])
            }
            val file = File(context.cacheDir, "idphoto_${System.currentTimeMillis()}.jpg")
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            }
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            navigator.push(Route.PrintSetup(files = listOf(PrintFile("IMAGE", uri.toString(), context.getString(R.string.idphoto_title)))))
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> IdPhotoMiuix(photo, specIndex, paperIndex, { picker.launch("image/*") }, { specIndex = it }, { paperIndex = it }, ::printResult)
        UiMode.Material -> IdPhotoMaterial(photo, specIndex, paperIndex, { picker.launch("image/*") }, { specIndex = it }, { paperIndex = it }, ::printResult)
    }
}

private fun decodeBitmap(context: android.content.Context, uri: android.net.Uri): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / (sample * 2) >= 2048) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

@Composable
private fun IdPhotoMiuix(
    photo: Bitmap?,
    specIndex: Int,
    paperIndex: Int,
    onPick: () -> Unit,
    onSpecChange: (Int) -> Unit,
    onPaperChange: (Int) -> Unit,
    onPrint: () -> Unit,
) {
    val navigator = LocalNavigator.current
    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = stringResource(R.string.idphoto_title),
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { navigator.pop() }) {
                        top.yukonga.miuix.kmp.basic.Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            item {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.preference.ArrowPreference(
                        title = stringResource(R.string.idphoto_pick),
                        summary = if (photo != null) stringResource(R.string.idphoto_picked) else stringResource(R.string.idphoto_pick_hint),
                        startAction = {
                            top.yukonga.miuix.kmp.basic.Icon(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.idphoto_pick), modifier = Modifier.padding(end = 12.dp))
                        },
                        onClick = onPick,
                    )
                    top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
                        title = stringResource(R.string.idphoto_spec),
                        items = IdPhotoLayout.specs.map { it.label },
                        selectedIndex = specIndex,
                        onSelectedIndexChange = onSpecChange,
                    )
                    top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
                        title = stringResource(R.string.idphoto_paper),
                        items = IdPhotoLayout.papers.map { it.label },
                        selectedIndex = paperIndex,
                        onSelectedIndexChange = onPaperChange,
                    )
                }
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(360.dp).padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val preview = photo?.let { IdPhotoLayout.layout(it, IdPhotoLayout.specs[specIndex], IdPhotoLayout.papers[paperIndex]) }
                    if (preview != null) {
                        Image(bitmap = preview.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    }
                }
            }
            item {
                top.yukonga.miuix.kmp.basic.Card(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.preference.ArrowPreference(
                        title = stringResource(R.string.idphoto_print),
                        startAction = {
                            top.yukonga.miuix.kmp.basic.Icon(Icons.Rounded.Print, stringResource(R.string.idphoto_print), modifier = Modifier.padding(end = 12.dp))
                        },
                        onClick = { if (photo != null) onPrint() },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdPhotoMaterial(
    photo: Bitmap?,
    specIndex: Int,
    paperIndex: Int,
    onPick: () -> Unit,
    onSpecChange: (Int) -> Unit,
    onPaperChange: (Int) -> Unit,
    onPrint: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.idphoto_title)) },
                colors = expressiveTopAppBarColors(),
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onPick,
                        headlineContent = { Text(stringResource(R.string.idphoto_pick)) },
                        supportingContent = { Text(if (photo != null) stringResource(R.string.idphoto_picked) else stringResource(R.string.idphoto_pick_hint)) },
                        leadingContent = { Icon(Icons.Rounded.AddPhotoAlternate, stringResource(R.string.idphoto_pick)) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        title = stringResource(R.string.idphoto_spec),
                        items = IdPhotoLayout.specs.map { it.label },
                        selectedIndex = specIndex,
                        onItemSelected = onSpecChange,
                    )
                }
                item {
                    SegmentedDropdownItem(
                        title = stringResource(R.string.idphoto_paper),
                        items = IdPhotoLayout.papers.map { it.label },
                        selectedIndex = paperIndex,
                        onItemSelected = onPaperChange,
                    )
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                contentAlignment = Alignment.Center,
            ) {
                val preview = photo?.let { IdPhotoLayout.layout(it, IdPhotoLayout.specs[specIndex], IdPhotoLayout.papers[paperIndex]) }
                if (preview != null) {
                    Image(bitmap = preview.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }

            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = { if (photo != null) onPrint() },
                        headlineContent = { Text(stringResource(R.string.idphoto_print)) },
                        leadingContent = { Icon(Icons.Rounded.Print, stringResource(R.string.idphoto_print)) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

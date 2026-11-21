package com.hpprint.app.ui.screen.idphoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.data.render.LayoutGen
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.SegmentedColumn
import com.hpprint.app.ui.component.material.SegmentedDropdownItem
import com.hpprint.app.ui.component.material.SegmentedListItem
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.theme.LocalEnableBlur
import com.hpprint.app.ui.util.BlurredBar
import com.hpprint.app.ui.util.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class IdPhotoSize(val key: String, val labelRes: Int, val wMm: Float, val hMm: Float)

private val ID_PHOTO_SIZES = listOf(
    IdPhotoSize("1inch", R.string.hp_photo_size_1inch, 25f, 35f),
    IdPhotoSize("small1", R.string.hp_photo_size_small1, 22f, 32f),
    IdPhotoSize("big1", R.string.hp_photo_size_big1, 33f, 48f),
    IdPhotoSize("2inch", R.string.hp_photo_size_2inch, 35f, 49f),
    IdPhotoSize("small2", R.string.hp_photo_size_small2, 35f, 45f),
)

private data class IdPhotoPaper(val labelRes: Int, val wMm: Float, val hMm: Float)

private val ID_PHOTO_PAPERS = listOf(
    IdPhotoPaper(R.string.hp_paper_6in, 102f, 152f),
    IdPhotoPaper(R.string.hp_paper_5in, 89f, 127f),
    IdPhotoPaper(R.string.hp_paper_7in, 127f, 178f),
    IdPhotoPaper(R.string.hp_paper_a4, 210f, 297f),
)

private val ID_PHOTO_BG_LABELS = listOf(
    R.string.hp_id_photo_bg_white,
    R.string.hp_id_photo_bg_blue,
    R.string.hp_id_photo_bg_red,
    R.string.hp_id_photo_bg_none,
)

private val ID_PHOTO_BG_VALUES: List<Int?> = listOf(
    LayoutGen.BG_WHITE,
    LayoutGen.BG_BLUE,
    LayoutGen.BG_RED,
    null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdPhotoScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoName by remember { mutableStateOf<String?>(null) }
    var sizeIndex by rememberSaveable { mutableStateOf(0) }
    var bgIndex by rememberSaveable { mutableStateOf(0) }
    var paperIndex by rememberSaveable { mutableStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoUri = uri
            photoName = uri.lastPathSegment
            preview = null
        }
    }

    fun generate() {
        val uri = photoUri ?: return
        val size = ID_PHOTO_SIZES[sizeIndex]
        val bg = ID_PHOTO_BG_VALUES[bgIndex]
        val paper = ID_PHOTO_PAPERS[paperIndex]
        scope.launch {
            val grid = withContext(Dispatchers.IO) {
                decodeBitmap(context, uri)?.let { LayoutGen.idPhotoGrid(it, size.wMm, size.hMm, bg, paper.wMm, paper.hMm) }
            }
            preview = grid
        }
    }

    fun print() {
        val p = preview ?: return
        scope.launch {
            val path = withContext(Dispatchers.IO) { LayoutGen.savePng(context, p, "idphoto") } ?: return@launch
            navigator.push(Route.PrintSetup(files = listOf(PrintFile("IMAGE", path, ""))))
        }
    }

    val onBack = { navigator.pop() }
    val onPick = { picker.launch("image/*") }
    when (LocalUiMode.current) {
        UiMode.Miuix -> IdPhotoMiuix(photoName, sizeIndex, { sizeIndex = it }, bgIndex, { bgIndex = it }, paperIndex, { paperIndex = it }, preview, ::generate, ::print, onPick, onBack)
        UiMode.Material -> IdPhotoMaterial(photoName, sizeIndex, { sizeIndex = it }, bgIndex, { bgIndex = it }, paperIndex, { paperIndex = it }, preview, ::generate, ::print, onPick, onBack)
    }
}

@Composable
private fun IdPhotoMiuix(
    photoName: String?,
    sizeIndex: Int,
    onSizeChange: (Int) -> Unit,
    bgIndex: Int,
    onBgChange: (Int) -> Unit,
    paperIndex: Int,
    onPaperChange: (Int) -> Unit,
    preview: Bitmap?,
    onGenerate: () -> Unit,
    onPrint: () -> Unit,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    val sizeItems = ID_PHOTO_SIZES.map { stringResource(it.labelRes) }
    val bgItems = ID_PHOTO_BG_LABELS.map { stringResource(it) }
    val paperItems = ID_PHOTO_PAPERS.map { stringResource(it.labelRes) }

    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    title = stringResource(R.string.hp_id_photo),
                    color = barColor,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                        }
                    },
                )
            }
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_id_photo_pick),
                    summary = photoName ?: stringResource(R.string.hp_id_photo_hint),
                    startAction = { Icon(Icons.Rounded.Image, stringResource(R.string.hp_id_photo_pick), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
                    onClick = onPick,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_id_photo_layout),
                    summary = sizeItems[sizeIndex],
                    items = sizeItems,
                    startAction = { Icon(Icons.Rounded.Style, stringResource(R.string.hp_id_photo_layout), modifier = Modifier.padding(end = 6.dp)) },
                    selectedIndex = sizeIndex,
                    onSelectedIndexChange = onSizeChange,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_paper_size),
                    summary = paperItems[paperIndex],
                    items = paperItems,
                    startAction = { Icon(Icons.Rounded.Style, stringResource(R.string.hp_paper_size), modifier = Modifier.padding(end = 6.dp)) },
                    selectedIndex = paperIndex,
                    onSelectedIndexChange = onPaperChange,
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.hp_id_photo_bg),
                    summary = bgItems[bgIndex],
                    items = bgItems,
                    startAction = { Icon(Icons.Rounded.Style, stringResource(R.string.hp_id_photo_bg), modifier = Modifier.padding(end = 6.dp)) },
                    selectedIndex = bgIndex,
                    onSelectedIndexChange = onBgChange,
                )
            }

            ArrowPreferenceCard(
                title = stringResource(R.string.hp_generate_preview),
                enabled = photoName != null,
                onClick = onGenerate,
                onBack = onBack,
            )

            preview?.let { bmp ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(ID_PHOTO_PAPERS[paperIndex].wMm / ID_PHOTO_PAPERS[paperIndex].hMm).padding(6.dp),
                    )
                }
            }

            ArrowPreferenceCard(
                title = stringResource(R.string.hp_print_button),
                enabled = preview != null,
                onClick = onPrint,
                onBack = onBack,
            )

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}

@Composable
private fun ArrowPreferenceCard(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onBack: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ArrowPreference(
            title = title,
            startAction = { Icon(Icons.Rounded.Print, title, modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground) },
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdPhotoMaterial(
    photoName: String?,
    sizeIndex: Int,
    onSizeChange: (Int) -> Unit,
    bgIndex: Int,
    onBgChange: (Int) -> Unit,
    paperIndex: Int,
    onPaperChange: (Int) -> Unit,
    preview: Bitmap?,
    onGenerate: () -> Unit,
    onPrint: () -> Unit,
    onPick: () -> Unit,
    onBack: () -> Unit,
) {
    val sizeItems = ID_PHOTO_SIZES.map { stringResource(it.labelRes) }
    val bgItems = ID_PHOTO_BG_LABELS.map { stringResource(it) }
    val paperItems = ID_PHOTO_PAPERS.map { stringResource(it.labelRes) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_id_photo)) },
                navigationIcon = { TopBarBackButton(onClick = onBack) },
                colors = expressiveTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    SegmentedListItem(
                        onClick = onPick,
                        headlineContent = { Text(stringResource(R.string.hp_id_photo_pick)) },
                        supportingContent = { Text(photoName ?: stringResource(R.string.hp_id_photo_hint)) },
                        leadingContent = { Icon(Icons.Rounded.Image, null) },
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.hp_id_photo_layout),
                        summary = sizeItems[sizeIndex],
                        items = sizeItems,
                        selectedIndex = sizeIndex,
                        onItemSelected = onSizeChange,
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.hp_paper_size),
                        summary = paperItems[paperIndex],
                        items = paperItems,
                        selectedIndex = paperIndex,
                        onItemSelected = onPaperChange,
                    )
                }
                item {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.hp_id_photo_bg),
                        summary = bgItems[bgIndex],
                        items = bgItems,
                        selectedIndex = bgIndex,
                        onItemSelected = onBgChange,
                    )
                }
            }

            Button(
                onClick = onGenerate,
                enabled = photoName != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hp_generate_preview))
            }

            preview?.let { bmp ->
                SegmentedColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(ID_PHOTO_PAPERS[paperIndex].wMm / ID_PHOTO_PAPERS[paperIndex].hMm).padding(8.dp),
                        )
                    }
                }
            }

            Button(
                onClick = onPrint,
                enabled = preview != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Print, null)
                Text(stringResource(R.string.hp_print_button), modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(
                Modifier.height(
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                        WindowInsets.captionBar.asPaddingValues().calculateBottomPadding(),
                ),
            )
        }
    }
}

private fun decodeBitmap(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    var w = bounds.outWidth
    var h = bounds.outHeight
    while (w > 0 && h > 0 && w / 2 >= 2048 && h / 2 >= 2048) {
        w /= 2; h /= 2; sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()
package com.hpprint.app.ui.screen.scan

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import com.hpprint.app.ui.util.ScanSaver
import com.hpprint.app.ui.viewmodel.ScanViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun scanTimestampName(): String =
    "扫描_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

@Composable
fun ScanPreviewScreen(imageUri: String) {
    val viewModel = viewModel<ScanViewModel>()
    val navigator = LocalNavigator.current
    val context = LocalContext.current

    val bitmap = remember(imageUri) {
        runCatching {
            if (imageUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { BitmapFactory.decodeStream(it) }
            } else {
                BitmapFactory.decodeFile(imageUri)
            }
        }.getOrNull()
    }

    val scanName = remember { scanTimestampName() }

    val onSave = {
        val bmp = bitmap
        if (bmp != null) {
            val saved = ScanSaver.saveBitmap(context, bmp, "$scanName.jpg") ?: imageUri
            viewModel.save(scanName, saved)
            navigator.pop()
        }
    }
    val onBack = { navigator.pop() }
    val onPrint = {
        navigator.push(
            Route.PrintSetup(
                files = listOf(PrintFile(kind = "IMAGE", sourceUri = imageUri, title = scanName)),
                webUrl = "",
            ),
        )
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> ScanPreviewMiuix(bitmap, onSave, onPrint, onBack)
        UiMode.Material -> ScanPreviewMaterial(bitmap, onSave, onPrint, onBack)
    }
}

@Composable
private fun ScanPreviewMiuix(
    bitmap: android.graphics.Bitmap?,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hp_scan_preview),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, null, tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
            )
        },
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_save),
                    summary = stringResource(R.string.hp_scan_saved),
                    startAction = {
                        Icon(Icons.Rounded.Save, stringResource(R.string.hp_save), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                    },
                    enabled = bitmap != null,
                    onClick = onSave,
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_print_button),
                    summary = stringResource(R.string.hp_print_setup),
                    startAction = {
                        Icon(Icons.Rounded.Print, stringResource(R.string.hp_print_button), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                    },
                    enabled = bitmap != null,
                    onClick = onPrint,
                )
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

@Composable
private fun ScanPreviewMaterial(
    bitmap: android.graphics.Bitmap?,
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_scan_preview)) },
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
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                )
            }
            Button(
                onClick = onSave,
                enabled = bitmap != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Save, null)
                Text(stringResource(R.string.hp_save), modifier = Modifier.padding(start = 8.dp))
            }
            Button(
                onClick = onPrint,
                enabled = bitmap != null,
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
package com.hpprint.app.ui.screen.webprint

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.component.material.ExpressiveScaffold
import com.hpprint.app.ui.component.material.TopBarBackButton
import com.hpprint.app.ui.component.material.expressiveTopAppBarColors
import com.hpprint.app.ui.component.miuix.EditText
import com.hpprint.app.ui.navigation3.LocalNavigator
import com.hpprint.app.ui.navigation3.PrintFile
import com.hpprint.app.ui.navigation3.Route
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WebPrintScreen() {
    val navigator = LocalNavigator.current
    var url by rememberSaveable { mutableStateOf("") }
    val normalized = normalizeUrl(url)
    val canPrint = normalized.isNotBlank()
    val onPrint = {
        if (canPrint) navigator.push(Route.PrintSetup(files = listOf(PrintFile("WEB", "", normalized)), webUrl = normalized))
    }
    val onBack = { navigator.pop() }

    when (LocalUiMode.current) {
        UiMode.Miuix -> WebPrintMiuix(url, { url = it }, canPrint, onPrint, onBack)
        UiMode.Material -> WebPrintMaterial(url, { url = it }, canPrint, onPrint, onBack)
    }
}

private fun normalizeUrl(url: String): String = when {
    url.isBlank() -> ""
    url.startsWith("http://") || url.startsWith("https://") -> url
    else -> "https://$url"
}

@Composable
private fun WebPrintMiuix(
    url: String,
    onUrlChange: (String) -> Unit,
    canPrint: Boolean,
    onPrint: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.hp_web_print_title),
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
                EditText(
                    title = stringResource(R.string.hp_url_hint),
                    value = url,
                    onValueChange = onUrlChange,
                    textHint = stringResource(R.string.hp_url_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = stringResource(R.string.hp_web_print_go),
                    summary = if (canPrint) normalizeUrl(url) else stringResource(R.string.hp_invalid_url),
                    startAction = {
                        Icon(Icons.Filled.Language, stringResource(R.string.hp_web_print_go), modifier = Modifier.padding(end = 12.dp), tint = MiuixTheme.colorScheme.onBackground)
                    },
                    enabled = canPrint,
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
private fun WebPrintMaterial(
    url: String,
    onUrlChange: (String) -> Unit,
    canPrint: Boolean,
    onPrint: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    ExpressiveScaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.hp_web_print_title)) },
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
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.hp_url_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                leadingIcon = { Icon(Icons.Filled.Language, null) },
                isError = url.isNotBlank() && !canPrint,
            )
            Button(
                onClick = onPrint,
                enabled = canPrint,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Language, null)
                Text(stringResource(R.string.hp_web_print_go), modifier = Modifier.padding(start = 8.dp))
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
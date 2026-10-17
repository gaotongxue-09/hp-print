package com.hpprint.app.ui.screen.about

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.dropUnlessResumed
import com.hpprint.app.BuildConfig
import com.hpprint.app.R
import com.hpprint.app.ui.LocalUiMode
import com.hpprint.app.ui.UiMode
import com.hpprint.app.ui.navigation3.LocalNavigator

@Composable
fun AboutScreen() {
    val navigator = LocalNavigator.current
    val uriHandler = LocalUriHandler.current
    val state = AboutUiState(
        title = stringResource(R.string.about),
        appName = stringResource(R.string.app_name),
        versionName = BuildConfig.VERSION_NAME,
        developerName = stringResource(R.string.hp_developer_name),
        developerUrl = "https://github.com/gaotongxue-09/hp-print.git",
        githubUrl = "https://github.com/gaotongxue-09/hp-print.git",
        coolapkUrl = "https://www.coolapk.com/feed/74103247?s=ZTcwMGExZjEyNTMxZDg2ZzZhZDJmMjYyega1664",
        authorAvatar = R.drawable.ic_author_avatar,
        references = listOf(
            ReferenceInfo(
                name = "HyperLight",
                description = stringResource(R.string.hp_ref_hyperlight_desc),
                url = "https://github.com/KiminonawaResa/HyperLight.git",
            ),
            ReferenceInfo(
                name = "miuix",
                description = stringResource(R.string.hp_ref_miuix_desc),
                url = "https://github.com/compose-miuix-ui/miuix.git",
            ),
            ReferenceInfo(
                name = "KernelSU",
                description = stringResource(R.string.hp_ref_kernelsu_desc),
                url = "https://github.com/tiann/KernelSU.git",
            ),
        ),
    )
    val actions = AboutScreenActions(
        onBack = dropUnlessResumed { navigator.pop() },
        onOpenLink = uriHandler::openUri,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> AboutScreenMiuix(state, actions)
        UiMode.Material -> AboutScreenMaterial(state, actions)
    }
}